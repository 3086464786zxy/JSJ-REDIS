package com.itmk.config.security.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.itmk.config.security.dto.AuthSessionDto;
import com.itmk.config.security.dto.PermissionDto;
import com.itmk.config.security.exception.CustomerAuthenionException;
import com.itmk.config.security.handler.LoginFailureHandler;
import com.itmk.config.security.service.AuthRedisService;
import com.itmk.config.security.service.PermissionCacheService;
import com.itmk.jwt.JwtUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** JWT 签名 + Redis 会话 + 权限 DTO 三层认证。 */
@Component("CheckTokenFilter")
public class CheckTokenFilter extends OncePerRequestFilter {
    @Value("#{'${ignore.url}'.split(',')}")
    private List<String> ignoreUrl = Collections.emptyList();

    private final JwtUtils jwtUtils;
    private final AuthRedisService authRedisService;
    private final PermissionCacheService permissionCacheService;
    private final LoginFailureHandler loginFailureHandler;
    @org.springframework.beans.factory.annotation.Autowired
    private com.itmk.config.security.service.SecurityStateService securityStates;

    public CheckTokenFilter(
            JwtUtils jwtUtils,
            AuthRedisService authRedisService,
            PermissionCacheService permissionCacheService,
            LoginFailureHandler loginFailureHandler) {
        this.jwtUtils = jwtUtils;
        this.authRedisService = authRedisService;
        this.permissionCacheService = permissionCacheService;
        this.loginFailureHandler = loginFailureHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String uri = request.getRequestURI();
            if (!ignoreUrl.contains(uri)
                    && !"/api/sysUser/loginOut".equals(uri)
                    && !uri.startsWith("/images/")) {
                validateToken(request);
            }
        } catch (org.springframework.dao.DataAccessException e) {
            loginFailureHandler.unavailable(response);
            return;
        } catch (AuthenticationException e) {
            loginFailureHandler.commence(request, response, e);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void validateToken(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new CustomerAuthenionException("请携带 Token");
        }

        DecodedJWT jwt;
        try {
            jwt = jwtUtils.jwtDecode(token);
        } catch (IllegalArgumentException e) {
            throw new CustomerAuthenionException("Token 无效或已过期");
        }

        Long userId = parseUserId(jwt.getClaim("userId").asString());
        String username = jwt.getClaim("username").asString();
        String sessionId = jwt.getClaim("sid").asString();
        if (userId == null || isBlank(username) || isBlank(sessionId)) {
            throw new CustomerAuthenionException("Token 缺少必要信息");
        }

        // JWT 合法还不够：Redis 会话被删除代表已退出或被管理员强制下线。
        AuthSessionDto session = authRedisService.getSession(userId, sessionId);
        if (session == null || !username.equals(session.getUsername())) {
            throw new CustomerAuthenionException("登录已失效，请重新登录");
        }
        request.setAttribute("audit.userId", userId);
        if (!securityStates.validSession(userId, username, sessionId, session.getSessionVersion()))
            throw new CustomerAuthenionException("登录已被撤销，请重新登录");

        PermissionDto permission = permissionCacheService.getOrLoad(userId, username);
        if (permission == null || !permission.isEnabled()) {
            throw new CustomerAuthenionException("账户不存在或已被禁用");
        }

        // 页面真实操作通过共享请求层标记；无操作的定时刷新和轮询不续期。
        if ("1".equals(request.getHeader("X-Session-Activity"))
                && !authRedisService.touchSession(userId, sessionId)) {
            throw new CustomerAuthenionException("登录已失效，请重新登录");
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        permission.getUsername(), null, permission.toAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("audit.userId", userId);
    }

    /** 优先使用标准 Bearer Header，暂时兼容旧的 token Header。 */
    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        String legacyToken = request.getHeader("token");
        return isBlank(legacyToken) ? null : legacyToken;
    }

    private Long parseUserId(String value) {
        try {
            return isBlank(value) ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
