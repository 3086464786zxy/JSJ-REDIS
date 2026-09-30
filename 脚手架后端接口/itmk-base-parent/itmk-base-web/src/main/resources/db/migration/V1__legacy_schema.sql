-- Empty databases only. Existing installations must validate the legacy schema and baseline at 1.
CREATE TABLE sys_user (
 user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(64) NOT NULL, password VARCHAR(100) NOT NULL,
 phone VARCHAR(32), email VARCHAR(128), sex VARCHAR(1), is_admin VARCHAR(1) NOT NULL DEFAULT '0',
 nick_name VARCHAR(64), is_account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
 is_account_non_locked BOOLEAN NOT NULL DEFAULT TRUE, is_credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
 is_enabled BOOLEAN NOT NULL DEFAULT TRUE, create_time DATETIME(3), update_time DATETIME(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE sys_role (
 role_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, role_name VARCHAR(64) NOT NULL,
 type VARCHAR(10), remark VARCHAR(256), create_time DATETIME(3), update_time DATETIME(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE sys_menu (
 menu_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, parent_id BIGINT NOT NULL DEFAULT 0,
 title VARCHAR(64), code VARCHAR(128), name VARCHAR(128), path VARCHAR(256), url VARCHAR(256),
 type VARCHAR(1), icon VARCHAR(64), parent_name VARCHAR(64), order_num VARCHAR(20),
 create_time DATETIME(3), update_time DATETIME(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE sys_user_role (
 user_role_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sys_role_menu (
 role_menu_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
