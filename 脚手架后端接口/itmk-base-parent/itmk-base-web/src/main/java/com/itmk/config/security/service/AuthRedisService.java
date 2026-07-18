package com.itmk.config.security.service;

import com.itmk.config.redis.RedisService;
import com.itmk.config.security.dto.AuthSessionDto;
import com.itmk.config.security.dto.RefreshTokenDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Redis 登录会话、一次性验证码与 RefreshToken。 */
@Service
public class AuthRedisService {
    private static final String SESSION_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";
    private static final String CAPTCHA_PREFIX = "auth:captcha:";
    private static final String REFRESH_PREFIX = "refresh:";
    private static final String REFRESH_USER_PREFIX = "refresh:user:";

    private final RedisService redisService;

    @Value("${app.security.captcha-ttl-seconds:120}")
    private long captchaTtlSeconds;

    public AuthRedisService(RedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * 会话键使用 {userId} hash tag，迁移 Redis Cluster 后同一用户的数据仍在同一槽位。
     */
    public void createSession(String sessionId, AuthSessionDto session, Duration ttl) {
        redisService.setJson(sessionKey(session.getUserId(), sessionId), session, ttl);
        redisService.addToSet(userSessionsKey(session.getUserId()), sessionId, ttl);
    }

    public AuthSessionDto getSession(Long userId, String sessionId) {
        return redisService.getJson(sessionKey(userId, sessionId), AuthSessionDto.class);
    }

    public void deleteSession(Long userId, String sessionId) {
        redisService.delete(sessionKey(userId, sessionId));
        redisService.removeFromSet(userSessionsKey(userId), sessionId);
    }

    /** 修改密码、禁用或删除用户时，撤销该用户的全部登录会话。 */
    public void deleteAllSessions(Long userId) {
        String sessionsKey = userSessionsKey(userId);
        Set<String> sessionIds = redisService.members(sessionsKey);
        List<String> keys = new ArrayList<>(sessionIds.size() + 1);
        for (String sessionId : sessionIds) {
            keys.add(sessionKey(userId, sessionId));
        }
        keys.add(sessionsKey);
        redisService.delete(keys);
    }

    public void saveCaptcha(String captchaId, String code) {
        redisService.set(captchaKey(captchaId), code, Duration.ofSeconds(captchaTtlSeconds));
    }

    /** 原子读取并删除，确保验证码即使校验失败也不能重放。 */
    public String consumeCaptcha(String captchaId) {
        return redisService.getAndDelete(captchaKey(captchaId));
    }

    private String sessionKey(Long userId, String sessionId) {
        return SESSION_PREFIX + "{" + userId + "}:" + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSIONS_PREFIX + "{" + userId + "}";
    }

    private String captchaKey(String captchaId) {
        return CAPTCHA_PREFIX + captchaId;
    }

    // ────────── RefreshToken 管理 ──────────

    /** 保存 RefreshToken，同时加入该用户的 Token 集合，方便批量管理。 */
    public void saveRefreshToken(String token, RefreshTokenDto dto, Duration ttl) {
        redisService.setJson(refreshKey(token), dto, ttl);
        redisService.addToSet(refreshUserKey(dto.getUserId()), token, ttl);
    }

    /** 读取 RefreshToken 信息。 */
    public RefreshTokenDto getRefreshToken(String token) {
        return redisService.getJson(refreshKey(token), RefreshTokenDto.class);
    }

    /** 删除单个 RefreshToken（退出登录或刷新时调用）。 */
    public void deleteRefreshToken(String token, Long userId) {
        redisService.delete(refreshKey(token));
        redisService.removeFromSet(refreshUserKey(userId), token);
    }

    /** 删除用户的所有 RefreshToken（修改密码、禁用、删除用户时调用）。 */
    public void deleteAllRefreshTokens(Long userId) {
        String userKey = refreshUserKey(userId);
        Set<String> tokens = redisService.members(userKey);
        List<String> keys = new ArrayList<>(tokens.size() + 1);
        for (String token : tokens) {
            keys.add(refreshKey(token));
        }
        keys.add(userKey);
        redisService.delete(keys);
    }

    private String refreshKey(String token) {
        return REFRESH_PREFIX + token;
    }

    private String refreshUserKey(Long userId) {
        return REFRESH_USER_PREFIX + "{" + userId + "}";
    }
}
