package com.itmk.config.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestAuditFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestAuditFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        MDC.put("requestId", id);
        response.setHeader("X-Request-ID", id);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().startsWith("/api/")
                    && !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
                String uri = request.getRequestURI().replaceAll("[\r\n\t]", "_");
                log.info(
                        "API mutation userId={} method={} path={} status={} durationMs={}",
                        request.getAttribute("audit.userId"),
                        request.getMethod(),
                        uri.substring(0, Math.min(uri.length(), 256)),
                        response.getStatus(),
                        (System.nanoTime() - start) / 1000000);
            }
            MDC.remove("requestId");
        }
    }
}
