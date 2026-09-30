package com.itmk.web.token.controller;

import com.itmk.config.security.dto.PermissionDto;
import com.itmk.config.security.dto.RefreshTokenDto;
import com.itmk.config.security.service.AuthRedisService;
import com.itmk.config.security.service.PermissionCacheService;
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

/**
 * Token 刷新接口，使用 RefreshToken（httpOnly Cookie）换取新的 AccessToken 和 RefreshToken。
 * 仅在 Redis 会话仍有效时刷新；刷新本身不延长闲置时间。
 */
@RestController
public class TokenController {

    private final JwtUtils jwtUtils;
    private final AuthRedisService authRedisService;
    private final PermissionCacheService permissionCacheService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.itmk.config.security.service.SecurityStateService securityStates;

    @Value("${app.security.refresh-token.cookie-name:refresh_token}")
    private String cookieName;

    @Value("${app.security.refresh-token.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.refresh-token.cookie-same-site:Lax}")
    private String cookieSameSite;

    public TokenController(JwtUtils jwtUtils, AuthRedisService authRedisService,
                           PermissionCacheService permissionCacheService) {
        this.jwtUtils = jwtUtils;
        this.authRedisService = authRedisService;
        this.permissionCacheService = permissionCacheService;
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
        String sessionId = stored.getSessionId();
        request.setAttribute("audit.userId", userId);
        var session = authRedisService.getSession(userId, sessionId);
        if (session == null || !username.equals(session.getUsername())
                || !securityStates.validSession(userId, username, sessionId, session.getSessionVersion())) {
            clearCookie(response);
            return ResultUtils.error("登录已被撤销，请重新登录", 401);
        }
        PermissionDto permission = permissionCacheService.getOrLoad(userId, username);
        if (permission == null || !permission.isEnabled()) {
            authRedisService.deleteSession(userId, sessionId);
            authRedisService.deleteRefreshToken(oldRefreshToken, userId);
            clearCookie(response);
            return ResultUtils.error("账户不存在或已被禁用", 401);
        }

        // 保持 sid 稳定，避免刷新导致其他标签页或并发请求的 JWT 失效。
        String newRefreshToken = authRedisService.newRefreshToken(userId);
        Duration refreshTtl = Duration.ofMinutes(jwtUtils.getRefreshExpiration());
        Map<String, String> claims = new HashMap<>();
        claims.put("userId", Long.toString(userId));
        claims.put("username", username);
        claims.put("sid", sessionId);
        String newAccessToken = jwtUtils.generateToken(claims);

        // 原子检查会话未过期、旧刷新凭证未被使用，再轮换凭证。绝不重新创建失效会话。
        if (!authRedisService.rotateRefreshToken(oldRefreshToken, newRefreshToken, stored, refreshTtl)) {
            return ResultUtils.error("登录已失效，请重新登录", 401);
        }

        // 6. 设置新 Cookie
        setCookie(response, newRefreshToken, refreshTtl);

        // 7. 返回新 AccessToken
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("idleTimeoutSeconds", authRedisService.getSessionIdleTimeout().toSeconds());
        return ResultUtils.success("Token 刷新成功", result);
    }

    /** 由前端真实交互触发并节流，鉴权过滤器负责原子延长当前会话。 */
    @PostMapping("/api/session/activity")
    public ResultVo activity() {
        return ResultUtils.success("会话有效");
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
