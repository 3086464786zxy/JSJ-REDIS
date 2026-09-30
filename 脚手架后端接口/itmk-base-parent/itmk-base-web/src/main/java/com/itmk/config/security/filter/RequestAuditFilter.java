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
    private final com.itmk.config.audit.AuditService audit;
    public RequestAuditFilter(com.itmk.config.audit.AuditService audit) { this.audit = audit; }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        MDC.put("requestId", id);
        response.setHeader("X-Request-ID", id);
        long start = System.nanoTime();
        boolean recorded = false;
        boolean api = request.getRequestURI().startsWith("/api/");
        boolean unhandled = false;
        try {
            if (api) {
                try { audit.append(request, "RECEIVED", null, null, null); recorded = true; }
                catch (RuntimeException e) {
                    audit.failure();
                    response.setStatus(503);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":503,\"msg\":\"审计服务暂时不可用，请稍后重试\",\"data\":null}");
                    return;
                }
            }
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            unhandled = true;
            throw e;
        } finally {
            if (recorded) {
                try { audit.append(request, "COMPLETED", unhandled ? 500 : response.getStatus(),
                        (Integer) request.getAttribute("audit.resultCode"), (System.nanoTime()-start)/1000000); }
                catch (RuntimeException e) {
                    audit.failure();
                    log.error("Audit outcome missing; durable receipt requestId={}", id);
                }
            }
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
