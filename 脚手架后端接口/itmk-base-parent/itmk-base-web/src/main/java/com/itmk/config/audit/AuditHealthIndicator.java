package com.itmk.config.audit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("auditHealthIndicator")
public class AuditHealthIndicator implements HealthIndicator {
    private final AuditService audit;
    public AuditHealthIndicator(AuditService audit, MeterRegistry metrics) {
        this.audit = audit;
        metrics.gauge("itmk.audit.incomplete", audit, AuditService::pendingCount);
    }
    @Override public Health health() {
        try { return audit.pendingCount() == 0 ? Health.up().build() : Health.down().build(); }
        catch (RuntimeException e) { return Health.down().build(); }
    }
}
