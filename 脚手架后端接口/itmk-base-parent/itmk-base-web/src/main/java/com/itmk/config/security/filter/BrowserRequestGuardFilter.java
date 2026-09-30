package com.itmk.config.security.filter;

import com.itmk.config.security.handler.CustomAccessDeineHandler;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * AJAX-only unsafe API requests; CORS remains disabled. Never allow credentialed cross-origin CORS
 * without revisiting this protection.
 */
@Component
public class BrowserRequestGuardFilter extends OncePerRequestFilter {
    private final CustomAccessDeineHandler denied;

    public BrowserRequestGuardFilter(CustomAccessDeineHandler denied) {
        this.denied = denied;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String site = request.getHeader("Sec-Fetch-Site");
        boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/")
                && unsafe
                && (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                        || "cross-site".equals(site)
                        || "same-site".equals(site))) {
            denied.handle(request, response, new AccessDeniedException("非法跨站请求"));
            return;
        }
        chain.doFilter(request, response);
    }
}
