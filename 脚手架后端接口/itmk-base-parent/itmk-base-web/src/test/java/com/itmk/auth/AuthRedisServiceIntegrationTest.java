package com.itmk.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itmk.config.redis.RedisService;
import com.itmk.config.security.dto.AuthSessionDto;
import com.itmk.config.security.dto.RefreshTokenDto;
import com.itmk.config.security.service.AuthRedisService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** 使用独立 Redis 端口运行：mvn -Dauth.redis.port=16389 test；不清空数据库。 */
@EnabledIfSystemProperty(named = "auth.redis.port", matches = "[0-9]+")
class AuthRedisServiceIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;
    private AuthRedisService auth;
    private long userId;
    private String sid;
    private RefreshTokenDto dto;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory(System.getProperty("auth.redis.host", "127.0.0.1"),
                Integer.parseInt(System.getProperty("auth.redis.port")));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        auth = new AuthRedisService(new RedisService(template, new ObjectMapper()));
        ReflectionTestUtils.setField(auth, "sessionIdleTimeoutMinutes", 30L);
        userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        sid = UUID.randomUUID().toString();
        dto = new RefreshTokenDto(userId, "integration", sid);
    }

    @AfterEach
    void cleanUp() {
        auth.deleteAllSessions(userId);
        auth.deleteAllRefreshTokens(userId);
        factory.destroy();
    }

    private String prepare(Duration ttl) {
        // 很早的登录时间仍可以续期：没有最长会话时限。
        auth.createSession(sid, new AuthSessionDto(userId, "integration", 0), ttl);
        String token = auth.newRefreshToken(userId);
        auth.saveRefreshToken(token, dto, Duration.ofDays(7));
        return token;
    }

    @Test
    void onlyOneConcurrentRefreshConsumesTheOldToken() throws Exception {
        String oldToken = prepare(Duration.ofSeconds(20));
        Long before = template.getExpire("auth:session:{" + userId + "}:" + sid, TimeUnit.MILLISECONDS);
        var calls = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 8; i++) {
            String next = auth.newRefreshToken(userId);
            calls.add(() -> auth.rotateRefreshToken(oldToken, next, dto, Duration.ofDays(7)));
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            int success = 0;
            for (var result : executor.invokeAll(calls)) if (result.get()) success++;
            assertEquals(1, success);
        }
        assertNull(auth.getRefreshToken(oldToken));
        assertEquals(1, template.opsForSet().size("refresh:user:{" + userId + "}"));
        assertTrue(template.getExpire("auth:session:{" + userId + "}:" + sid, TimeUnit.MILLISECONDS) <= before);
        assertEquals(0, auth.getSession(userId, sid).getLoginTime());
    }

    @Test
    void activityRenewsButCannotRestoreDeletedSession() {
        prepare(Duration.ofSeconds(2));
        assertTrue(auth.touchSession(userId, sid));
        assertTrue(template.getExpire("auth:session:{" + userId + "}:" + sid, TimeUnit.SECONDS) >= 1790);
        auth.deleteSession(userId, sid);
        assertFalse(auth.touchSession(userId, sid));
        assertNull(auth.getSession(userId, sid));
    }

    @Test
    void refreshCookieCannotReviveIdleExpiredSession() {
        String oldToken = prepare(Duration.ofMillis(50));
        await().atMost(Duration.ofSeconds(3)).until(() -> auth.getSession(userId, sid) == null);
        assertNotNull(auth.getRefreshToken(oldToken));
        assertFalse(auth.rotateRefreshToken(oldToken, auth.newRefreshToken(userId), dto, Duration.ofDays(7)));
        assertFalse(auth.touchSession(userId, sid));
        assertNull(auth.getSession(userId, sid));
    }
}

