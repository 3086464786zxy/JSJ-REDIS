package com.itmk.config.security.service;

import com.itmk.config.redis.RedisService;
import com.itmk.config.security.detailservice.CustomerUserDetailService;
import com.itmk.config.security.dto.PermissionDto;
import com.itmk.web.sys_user.entity.SysUser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** 权限缓存使用全局版本号失效。 角色/菜单变更只需 INCR 一次，无需用 KEYS/SCAN 扫描大量用户缓存。 */
@Service
public class PermissionCacheService {
    private static final String VERSION_KEY = "authz:global-version";
    private static final String PERMISSION_PREFIX = "authz:user:";

    private final RedisService redisService;
    private final CustomerUserDetailService userDetailService;

    @Value("${app.security.permission-ttl-seconds:900}")
    private long permissionTtlSeconds;

    @Value("${app.security.permission-ttl-jitter-seconds:180}")
    private long permissionTtlJitterSeconds;

    public PermissionCacheService(
            RedisService redisService, CustomerUserDetailService userDetailService) {
        this.redisService = redisService;
        this.userDetailService = userDetailService;
    }

    public PermissionDto getOrLoad(Long userId, String expectedUsername) {
        String version = currentVersion(userId);
        String key = permissionKey(userId, version);
        PermissionDto permission = redisService.getJson(key, PermissionDto.class);
        if (permission == null) {
            permission = userDetailService.loadPermissionByUserId(userId);
            if (permission == null) {
                return null;
            }
            cache(key, permission);
        }
        return java.util.Objects.equals(expectedUsername, permission.getUsername())
                ? permission
                : null;
    }

    /** Re-read authoritative permissions under the version captured before loading. */
    public void cacheLoginUser(SysUser user) {
        getOrLoad(user.getUserId(), user.getUsername());
    }

    public void invalidateAll() {
        afterCommit(() -> redisService.increment(VERSION_KEY));
    }

    /** 用户资料或角色分配变化时只失效该用户，避免大用户量下全量回源。 */
    public void invalidateUser(Long userId) {
        afterCommit(() -> redisService.increment("authz:user-version:{" + userId + "}"));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    private void cache(String key, PermissionDto permission) {
        long jitter =
                permissionTtlJitterSeconds <= 0
                        ? 0
                        : ThreadLocalRandom.current().nextLong(permissionTtlJitterSeconds + 1);
        redisService.setJson(key, permission, Duration.ofSeconds(permissionTtlSeconds + jitter));
    }

    private String currentVersion(Long userId) {
        String global = redisService.get(VERSION_KEY);
        String user = redisService.get("authz:user-version:{" + userId + "}");
        return (global == null ? "0" : global) + ":" + (user == null ? "0" : user);
    }

    private String permissionKey(Long userId, String version) {
        return PERMISSION_PREFIX + "{" + userId + "}:v" + version;
    }
}
