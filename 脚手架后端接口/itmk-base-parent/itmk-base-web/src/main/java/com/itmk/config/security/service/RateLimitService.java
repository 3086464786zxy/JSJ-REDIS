package com.itmk.config.security.service;

import com.itmk.config.redis.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/** 基于 Lua 的分布式固定窗口限流，计数与设置 TTL 在 Redis 内原子完成。 */
@Service
public class RateLimitService {
    private static final RedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if count > tonumber(ARGV[1]) then
                return 0
            end
            return 1
            """, Long.class);

    private final RedisService redisService;

    @Value("${app.security.rate-limit.captcha-per-minute:20}")
    private int captchaPerMinute;

    @Value("${app.security.rate-limit.login-ip-per-minute:20}")
    private int loginIpPerMinute;

    @Value("${app.security.rate-limit.login-user-per-minute:10}")
    private int loginUserPerMinute;

    public RateLimitService(RedisService redisService) {
        this.redisService = redisService;
    }

    public boolean allowCaptcha(HttpServletRequest request) {
        return allow("rate:captcha:ip:" + hash(clientIp(request)), captchaPerMinute, Duration.ofMinutes(1));
    }

    public boolean allowLogin(HttpServletRequest request, String username) {
        boolean ipAllowed = allow(
                "rate:login:ip:" + hash(clientIp(request)), loginIpPerMinute, Duration.ofMinutes(1)
        );
        boolean userAllowed = allow(
                "rate:login:user:" + hash(normalize(username)), loginUserPerMinute, Duration.ofMinutes(1)
        );
        return ipAllowed && userAllowed;
    }

    private boolean allow(String key, int limit, Duration window) {
        Long result = redisService.execute(
                LIMIT_SCRIPT,
                Collections.singletonList(key),
                Integer.toString(limit),
                Long.toString(window.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }

    private String clientIp(HttpServletRequest request) {
        // 生产环境应由可信反向代理统一设置 RemoteAddr，不能直接信任客户端伪造的请求头。
        return request.getRemoteAddr();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String hash(String value) {
        return DigestUtils.sha256Hex(value);
    }
}
