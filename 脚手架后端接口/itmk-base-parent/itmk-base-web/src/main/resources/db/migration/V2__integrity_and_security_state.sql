-- Duplicates/orphans must be resolved before migration; never delete business data automatically.
ALTER TABLE sys_user ADD CONSTRAINT uk_sys_user_username UNIQUE(username),
 ADD COLUMN permission_version BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sys_user_role ADD CONSTRAINT uk_sys_user_role UNIQUE(user_id,role_id),
 ADD INDEX ix_user_role_role(role_id),
 ADD CONSTRAINT fk_user_role_user FOREIGN KEY(user_id) REFERENCES sys_user(user_id),
 ADD CONSTRAINT fk_user_role_role FOREIGN KEY(role_id) REFERENCES sys_role(role_id);
ALTER TABLE sys_role_menu ADD CONSTRAINT uk_sys_role_menu UNIQUE(role_id,menu_id),
 ADD INDEX ix_role_menu_menu(menu_id),
 ADD CONSTRAINT fk_role_menu_role FOREIGN KEY(role_id) REFERENCES sys_role(role_id),
 ADD CONSTRAINT fk_role_menu_menu FOREIGN KEY(menu_id) REFERENCES sys_menu(menu_id);
CREATE INDEX ix_menu_parent ON sys_menu(parent_id);
CREATE INDEX ix_user_created ON sys_user(create_time,user_id);
CREATE INDEX ix_role_created ON sys_role(create_time,role_id);
CREATE TABLE authz_state (
 state_id INT NOT NULL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_authz_singleton CHECK(state_id=1)
) ENGINE=InnoDB;
INSERT INTO authz_state(state_id,version) VALUES(1,0);
CREATE TABLE auth_revoked_session (
 session_id VARCHAR(64) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL,
 expires_at DATETIME(3) NOT NULL, INDEX ix_revoked_expiry(expires_at)
) ENGINE=InnoDB;
-- No FK to user: deletion must retain audit evidence.
CREATE TABLE sys_audit_event (
 event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, request_id VARCHAR(36) NOT NULL,
 phase VARCHAR(24) NOT NULL, user_id BIGINT, method VARCHAR(16) NOT NULL,
 path VARCHAR(256) NOT NULL, remote_address VARCHAR(64), http_status INT, result_code INT,
 duration_ms BIGINT, target_id BIGINT, occurred_at DATETIME(3) NOT NULL,
 INDEX ix_audit_request(request_id,event_id), INDEX ix_audit_time(occurred_at,event_id),
 INDEX ix_audit_user(user_id,occurred_at,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
