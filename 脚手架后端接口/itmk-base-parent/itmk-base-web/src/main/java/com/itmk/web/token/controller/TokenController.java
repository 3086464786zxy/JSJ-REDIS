package com.itmk.web.token.controller;

import com.itmk.config.security.dto.AuthSessionDto;
import com.itmk.config.security.dto.RefreshTokenDto;
import com.itmk.config.security.service.AuthRedisService;
import com.itmk.jwt.JwtUtils;
import com.itmk.utils.ResultUtils;
import com.itmk.utils.ResultVo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Token 刷新接口，使用 RefreshToken（httpOnly Cookie）换取新的 AccessToken 和 RefreshToken。
 * 每次刷新伴随 Session Rotation：旧会话删除，新会话创建。
 */
@RestController
public class TokenController {

    private final JwtUtils jwtUtils;
    private final AuthRedisService authRedisService;

    @Value("${app.security.refresh-token.cookie-name:refresh_token}")
    private String cookieName;

    @Value("${app.security.refresh-token.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.refresh-token.cookie-same-site:Lax}")
    private String cookieSameSite;

    public TokenController(JwtUtils jwtUtils, AuthRedisService authRedisService) {
        this.jwtUtils = jwtUtils;
        this.authRedisService = authRedisService;
    }

    @PostMapping("/api/refresh")
    public ResultVo refresh(HttpServletRequest request, HttpServletResponse response) {
        String oldRefreshToken = extractCookie(request, cookieName);
        if (oldRefreshToken == null || oldRefreshToken.isEmpty()) {
            return ResultUtils.error("缺少 RefreshToken", 401);
        }

        // 1. 验证 RefreshToken 在 Redis 中存在
        RefreshTokenDto stored = authRedisService.getRefreshToken(oldRefreshToken);
        if (stored == null) {
            clearCookie(response);
            return ResultUtils.error("RefreshToken 无效或已过期，请重新登录", 401);
        }

        Long userId = stored.getUserId();
        String username = stored.getUsername();
        String oldSessionId = stored.getSessionId();

        // 2. Session Rotation：删除旧会话
        authRedisService.deleteSession(userId, oldSessionId);

        // 3. 创建新会话
        String newSessionId = UUID.randomUUID().toString();
        Duration sessionTtl = Duration.ofMinutes(jwtUtils.getExpiration());
        authRedisService.createSession(
                newSessionId,
                new AuthSessionDto(userId, username, System.currentTimeMillis()),
                sessionTtl
        );

        // 4. RefreshToken Rotation：删除旧 Token，签发新 Token
        authRedisService.deleteRefreshToken(oldRefreshToken, userId);

        String newRefreshToken = UUID.randomUUID().toString();
        Duration refreshTtl = Duration.ofMinutes(jwtUtils.getRefreshExpiration());
        RefreshTokenDto newDto = new RefreshTokenDto(userId, username, newSessionId);
        authRedisService.saveRefreshToken(newRefreshToken, newDto, refreshTtl);

        // 5. 签发新 AccessToken（JWT，含新 sid）
        Map<String, String> claims = new HashMap<>();
        claims.put("userId", Long.toString(userId));
        claims.put("username", username);
        claims.put("sid", newSessionId);
        String newAccessToken = jwtUtils.generateToken(claims);

        // 6. 设置新 Cookie
        setCookie(response, newRefreshToken, refreshTtl);

        // 7. 返回新 AccessToken
        Map<String, String> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        return ResultUtils.success("Token 刷新成功", result);
    }

    // ────────── Cookie 工具方法 ──────────

    /** 从请求中提取指定 Cookie 的值。 */
    public static String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 设置 httpOnly Cookie，Path 限定在 /api/ 下。 */
    public void setCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/")
                .maxAge(maxAge)
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 清除客户端 Cookie。 */
    public void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/")
                .maxAge(0)
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
