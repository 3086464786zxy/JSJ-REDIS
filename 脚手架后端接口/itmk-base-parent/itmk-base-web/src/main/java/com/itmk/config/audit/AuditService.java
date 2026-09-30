package com.itmk.config.audit;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import com.itmk.web.sys_audit.mapper.AuditEventMapper;
import com.itmk.web.sys_audit.entity.AuditEvent;
import com.itmk.web.sys_audit.entity.PendingAuditRequest;
import com.itmk.web.sys_audit.mapper.PendingAuditRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Append-only request receipt/outcome. No passwords, tokens, cookies, query strings or bodies. */
@Service
public class AuditService {
    private final AuditEventMapper db;
    private final MeterRegistry metrics;
    private final PendingAuditRequestMapper pending;
    public AuditService(AuditEventMapper db, PendingAuditRequestMapper pending, MeterRegistry metrics) {
        this.db = db; this.pending=pending; this.metrics = metrics;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(HttpServletRequest request, String phase, Integer status, Integer result, Long duration) {
        insert(request,phase,status,result,duration);
        if("RECEIVED".equals(phase)) {
            var receipt=new PendingAuditRequest();
            receipt.setRequestId(MDC.get("requestId")); receipt.setReceivedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            if(pending.insert(receipt)!=1) throw new IllegalStateException("审计请求状态写入失败");
        } else if("COMPLETED".equals(phase)) pending.deleteById(MDC.get("requestId"));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void appendMutation(HttpServletRequest request) { insert(request,"CHANGED",null,200,null); }
    private void insert(HttpServletRequest request, String phase, Integer status, Integer result, Long duration) {
        Object user = request.getAttribute("audit.userId");
        Object target = request.getAttribute("audit.targetId");
        AuditEvent event=new AuditEvent();
        event.setRequestId(MDC.get("requestId")); event.setPhase(phase);
        event.setUserId(user instanceof Long id ? id : null);
        event.setTargetId(target instanceof Long id ? id : null);
        event.setMethod(safe(request.getMethod(),16)); event.setPath(safe(request.getRequestURI(),256));
        event.setRemoteAddress(safe(request.getRemoteAddr(),64)); event.setHttpStatus(status);
        event.setResultCode(result); event.setDurationMs(duration); event.setOccurredAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        event.setDetails((String) request.getAttribute("audit.details"));
        if (db.insert(event) != 1)
            throw new IllegalStateException("审计写入失败");
    }
    public void failure() { metrics.counter("itmk.audit.write.failures").increment(); }
    private String safe(String value, int limit) {
        if (value == null) return null;
        String safe = value.replaceAll("[\\r\\n\\t]", "_");
        return safe.substring(0, Math.min(safe.length(), limit));
    }
    public long pendingCount() {
        return db.pendingCount(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(5));
    }
}
