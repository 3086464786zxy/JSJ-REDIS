package com.itmk.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.itmk.config.security.dto.AuthSessionDto;
import com.itmk.config.security.dto.PermissionDto;
import com.itmk.config.security.dto.RefreshTokenDto;
import com.itmk.config.security.filter.CheckTokenFilter;
import com.itmk.config.security.handler.LoginFailureHandler;
import com.itmk.config.security.service.AuthRedisService;
import com.itmk.config.security.service.PermissionCacheService;
import com.itmk.jwt.JwtUtils;
import com.itmk.utils.ResultVo;
import com.itmk.web.sys_user.controller.SysUserController;
import com.itmk.web.token.controller.TokenController;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

class SessionAuthenticationTest {
    private final AuthRedisService auth = mock(AuthRedisService.class);
    private final PermissionCacheService permissions = mock(PermissionCacheService.class);
    private final LoginFailureHandler failure = mock(LoginFailureHandler.class);
    private final JwtUtils jwt = new JwtUtils();
    private TokenController controller;
    private CheckTokenFilter filter;
    private final RefreshTokenDto refresh = new RefreshTokenDto(7L, "tester", "same-session");
    private final String oldToken = "7.00000000-0000-0000-0000-000000000000";

    @BeforeEach
    void setUp() {
        jwt.setIssuer("test");
        jwt.setAudience("test");
        jwt.setSecret("session-tests-only-secret-32-bytes-minimum");
        jwt.setExpiration(15);
        jwt.setRefreshExpiration(10080);
        jwt.init();
        controller = new TokenController(jwt, auth, permissions);
        ReflectionTestUtils.setField(controller, "cookieName", "refresh_token");
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Lax");
        filter = new CheckTokenFilter(jwt, auth, permissions, failure);
        ReflectionTestUtils.setField(filter, "ignoreUrl", java.util.List.of("/api/refresh"));
        when(auth.getRefreshToken(oldToken)).thenReturn(refresh);
        when(auth.newRefreshToken(7L)).thenReturn("7.11111111-1111-1111-1111-111111111111");
        when(auth.getSessionIdleTimeout()).thenReturn(Duration.ofMinutes(30));
        when(permissions.getOrLoad(7L, "tester"))
                .thenReturn(new PermissionDto(7L, "tester", true, Set.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest refreshRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/refresh");
        request.setCookies(new Cookie("refresh_token", oldToken));
        return request;
    }

    private MockHttpServletRequest protectedRequest(boolean activity) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/session/activity");
        String token =
                jwt.generateToken(
                        Map.of("userId", "7", "username", "tester", "sid", "same-session"));
        request.addHeader("Authorization", "Bearer " + token);
        if (activity) request.addHeader("X-Session-Activity", "1");
        return request;
    }

    @Test
    void refreshKeepsSessionAndDoesNotRenewIdleTime() {
        when(auth.rotateRefreshToken(eq(oldToken), anyString(), eq(refresh), any()))
                .thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResultVo result = controller.refresh(refreshRequest(), response);
        assertEquals(200, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(
                "same-session",
                jwt.jwtDecode((String) data.get("accessToken")).getClaim("sid").asString());
        assertEquals(1800L, data.get("idleTimeoutSeconds"));
        assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
        verify(auth, never()).createSession(anyString(), any(), any());
        verify(auth, never()).touchSession(anyLong(), anyString());
    }

    @Test
    void expiredOrRevokedSessionCannotBeRecreatedByRefresh() {
        when(auth.rotateRefreshToken(eq(oldToken), anyString(), eq(refresh), any()))
                .thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(401, controller.refresh(refreshRequest(), response).getCode());
        assertNull(response.getHeader("Set-Cookie"));
        verify(auth, never()).createSession(anyString(), any(), any());
    }

    @Test
    void disabledUserCannotRefresh() {
        when(permissions.getOrLoad(7L, "tester"))
                .thenReturn(new PermissionDto(7L, "tester", false, Set.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(401, controller.refresh(refreshRequest(), response).getCode());
        verify(auth).deleteSession(7L, "same-session");
        verify(auth, never()).rotateRefreshToken(anyString(), anyString(), any(), any());
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }

    @Test
    void missingRefreshCookieIsRejected() {
        assertEquals(
                401,
                controller
                        .refresh(new MockHttpServletRequest(), new MockHttpServletResponse())
                        .getCode());
        verify(auth, never()).rotateRefreshToken(anyString(), anyString(), any(), any());
    }

    @Test
    void activityRenewsOnlyExistingAuthenticatedSession() throws Exception {
        when(auth.getSession(7L, "same-session")).thenReturn(new AuthSessionDto(7L, "tester", 0));
        when(auth.touchSession(7L, "same-session")).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(protectedRequest(true), new MockHttpServletResponse(), chain);
        verify(auth).touchSession(7L, "same-session");
        verify(chain).doFilter(any(), any());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void backgroundRequestsDoNotRenewIdleTime() throws Exception {
        when(auth.getSession(7L, "same-session")).thenReturn(new AuthSessionDto(7L, "tester", 0));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(protectedRequest(false), new MockHttpServletResponse(), chain);
        verify(auth, never()).touchSession(anyLong(), anyString());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void sessionDeletedBetweenValidationAndTouchIsRejected() throws Exception {
        when(auth.getSession(7L, "same-session")).thenReturn(new AuthSessionDto(7L, "tester", 0));
        when(auth.touchSession(7L, "same-session")).thenReturn(false);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(protectedRequest(true), new MockHttpServletResponse(), chain);
        verify(chain, never()).doFilter(any(), any());
        verify(failure).commence(any(), any(), any());
    }

    @Test
    void expiredJwtCannotReachProtectedEndpoint() throws Exception {
        jwt.setExpiration(-1);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(protectedRequest(true), new MockHttpServletResponse(), chain);
        verify(chain, never()).doFilter(any(), any());
        verify(auth, never()).touchSession(anyLong(), anyString());
        verify(failure).commence(any(), any(), any());
    }

    @Test
    void refreshAndLogoutRemainReachableWithoutValidAccessToken() throws Exception {
        for (String path : new String[] {"/api/refresh", "/api/sysUser/loginOut"}) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(
                    new MockHttpServletRequest("POST", path), new MockHttpServletResponse(), chain);
            verify(chain).doFilter(any(), any());
        }
        verifyNoInteractions(failure);
    }

    @Test
    void logoutRevokesSessionThroughCookieEvenWhenAccessTokenExpired() {
        SysUserController users = new SysUserController();
        ReflectionTestUtils.setField(users, "jwtUtils", jwt);
        ReflectionTestUtils.setField(users, "authRedisService", auth);
        ReflectionTestUtils.setField(users, "cookieName", "refresh_token");
        ReflectionTestUtils.setField(users, "cookieSameSite", "Lax");
        jwt.setExpiration(-1);
        MockHttpServletRequest request = protectedRequest(false);
        request.setCookies(new Cookie("refresh_token", oldToken));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(200, users.loginOut(request, response).getCode());
        verify(auth).deleteSession(7L, "same-session");
        verify(auth).deleteRefreshToken(oldToken, 7L);
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }
}
