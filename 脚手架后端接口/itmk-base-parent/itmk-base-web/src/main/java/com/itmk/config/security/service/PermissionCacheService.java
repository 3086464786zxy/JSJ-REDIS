package com.itmk.config.security.service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itmk.config.redis.RedisService;
import com.itmk.config.security.detailservice.CustomerUserDetailService;
import com.itmk.config.security.dto.PermissionDto;
import com.itmk.web.sys_user.entity.SysUser;

/**
 * 权限缓存使用全局版本号失效。
 * 角色/菜单变更只需 INCR 一次，无需用 KEYS/SCAN 扫描大量用户缓存。
 */
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

    public PermissionCacheService(RedisService redisService,
                                  CustomerUserDetailService userDetailService) {
        this.redisService = redisService;
        this.userDetailService = userDetailService;
    }

    public PermissionDto getOrLoad(Long userId, String expectedUsername) {
        String version = currentVersion();
        String key = permissionKey(userId, version);
        PermissionDto permission = redisService.getJson(key, PermissionDto.class);
        if (permission == null) {
            permission = userDetailService.loadPermissionByUserId(userId);
            if (permission == null) {
                return null;
            }
            cache(key, permission);
        }
        return java.util.Objects.equals(
            expectedUsername,
            permission.getUsername()
        ) ? permission : null;
    }

    /** 登录成功时复用已经查询出的权限，避免下一次请求再次访问数据库。 */
    public void cacheLoginUser(SysUser user) {
        Set<String> permissions = user.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
        PermissionDto dto = new PermissionDto(
                user.getUserId(), user.getUsername(), user.isEnabled(), permissions
        );
        cache(permissionKey(user.getUserId(), currentVersion()), dto);
    }

    public void invalidateAll() {
        redisService.increment(VERSION_KEY);
    }

    /** 用户资料或角色分配变化时只失效该用户，避免大用户量下全量回源。 */
    public void invalidateUser(Long userId) {
        redisService.delete(permissionKey(userId, currentVersion()));
    }

    private void cache(String key, PermissionDto permission) {
        long jitter = permissionTtlJitterSeconds <= 0 ? 0
                : ThreadLocalRandom.current().nextLong(permissionTtlJitterSeconds + 1);
        redisService.setJson(key, permission, Duration.ofSeconds(permissionTtlSeconds + jitter));
    }

    private String currentVersion() {
        String version = redisService.get(VERSION_KEY);
        return version == null ? "0" : version;
    }

    private String permissionKey(Long userId, String version) {
        return PERMISSION_PREFIX + "{" + userId + "}:v" + version;
    }
}
