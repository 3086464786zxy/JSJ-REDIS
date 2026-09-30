ALTER TABLE sys_audit_event ADD COLUMN details TEXT;
CREATE TABLE sys_audit_pending (
 request_id VARCHAR(36) NOT NULL PRIMARY KEY, received_at DATETIME(3) NOT NULL,
 INDEX ix_audit_pending_time(received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO sys_audit_pending(request_id,received_at)
SELECT r.request_id,MIN(r.occurred_at) FROM sys_audit_event r
WHERE r.phase='RECEIVED' AND NOT EXISTS
 (SELECT 1 FROM sys_audit_event c WHERE c.request_id=r.request_id AND c.phase='COMPLETED')
GROUP BY r.request_id;
