package com.itmk.config.security.service;

import com.itmk.config.security.dto.SecurityState;
import io.micrometer.core.instrument.MeterRegistry;
import com.itmk.web.sys_user.mapper.SecurityStateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class SecurityStateService {
    private final SecurityStateMapper db;
    private final MeterRegistry metrics;
    public SecurityStateService(SecurityStateMapper db, MeterRegistry metrics) { this.db = db; this.metrics = metrics; }
    public SecurityState read(Long userId, String sessionId) {
        return db.read(userId,sessionId);
    }
    public boolean validSession(Long userId, String username, String sessionId, Long version) {
        SecurityState state = read(userId, sessionId);
        boolean valid = state != null && state.enabled() && !state.revoked() && version != null
                && version == state.sessionVersion() && Objects.equals(username, state.username());
        if (!valid) metrics.counter("itmk.security.session.rejected").increment();
        return valid;
    }
    public void invalidateAll() {
        requireTransaction();
        if (db.invalidateAll() != 1)
            throw new IllegalStateException("权限版本状态不存在");
    }
    public void invalidateUser(Long id) {
        requireTransaction();
        if (db.invalidateUser(id) != 1)
            throw new IllegalArgumentException("用户不存在");
    }
    public void revokeUser(Long id) {
        requireTransaction();
        if (db.revokeUser(id) != 1)
            throw new IllegalArgumentException("用户不存在");
    }
    /** Durable logout even if deleting Redis keys fails. */
    @Transactional(rollbackFor = Exception.class)
    public void revokeSession(Long id, String sid) {
        if (id == null || sid == null || !sid.matches("[a-zA-Z0-9-]{1,64}"))
            throw new IllegalArgumentException("会话信息无效");
        db.revokeSession(id,sid,java.sql.Timestamp.from(java.time.Instant.now().plus(java.time.Duration.ofDays(7))));
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("权限版本必须与业务修改在同一数据库事务中提交");
    }
}
