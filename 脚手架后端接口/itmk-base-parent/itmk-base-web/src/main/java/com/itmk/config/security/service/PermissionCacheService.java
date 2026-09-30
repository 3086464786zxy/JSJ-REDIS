package com.itmk.config.security.service;

import com.itmk.config.redis.RedisService;
import com.itmk.config.security.detailservice.CustomerUserDetailService;
import com.itmk.config.security.dto.PermissionDto;
import com.itmk.config.security.dto.SecurityState;
import com.itmk.web.sys_user.entity.SysUser;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Database epochs commit with permission writes. Redis invalidation is no longer a correctness dependency. */
@Service
public class PermissionCacheService {
    private final RedisService redis;
    private final CustomerUserDetailService details;
    private final SecurityStateService states;
    private final MeterRegistry metrics;
    @Value("${app.security.permission-ttl-seconds:900}") private long ttl;
    @Value("${app.security.permission-ttl-jitter-seconds:180}") private long jitter;
    public PermissionCacheService(RedisService redis, CustomerUserDetailService details,
            SecurityStateService states, MeterRegistry metrics) {
        this.redis = redis; this.details = details; this.states = states; this.metrics = metrics;
    }
    public PermissionDto getOrLoad(Long id, String username) {
        for (int attempt = 0; attempt < 3; attempt++) {
            SecurityState before = states.read(id, null);
            if (before == null || !before.enabled() || !Objects.equals(username, before.username())) return null;
            String key = "authz:db-user:{" + id + "}:v" + before.cacheVersion();
            PermissionDto permission = null;
            try { permission = redis.getJson(key, PermissionDto.class); }
            catch (DataAccessException e) { cacheFailure(); }
            if (permission == null) {
                metrics.counter("itmk.security.permission.cache", "result", "miss").increment();
                permission = details.loadPermissionByUserId(id);
                if (permission == null) return null;
                try {
                    long extra = jitter <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitter + 1);
                    redis.setJson(key, permission, Duration.ofSeconds(ttl + extra));
                } catch (DataAccessException e) { cacheFailure(); }
            } else metrics.counter("itmk.security.permission.cache", "result", "hit").increment();
            SecurityState after = states.read(id, null);
            if (before.equals(after)) return Objects.equals(username, permission.getUsername()) ? permission : null;
            metrics.counter("itmk.security.permission.retry").increment();
        }
        throw new TransientDataAccessResourceException("权限正在变更，请重试");
    }
    private void cacheFailure() { metrics.counter("itmk.security.permission.cache.failures").increment(); }
    public void cacheLoginUser(SysUser user) { getOrLoad(user.getUserId(), user.getUsername()); }
    public void invalidateAll() { states.invalidateAll(); }
    public void invalidateUser(Long id) { states.invalidateUser(id); }
}
