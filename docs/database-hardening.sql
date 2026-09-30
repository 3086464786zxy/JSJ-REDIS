-- 只读预检查。不要在未备份、未清理数据和未核对已有索引时执行 ALTER。
SELECT username, COUNT(*) FROM sys_user GROUP BY username HAVING COUNT(*) > 1;
SELECT user_id, role_id, COUNT(*) FROM sys_user_role GROUP BY user_id, role_id HAVING COUNT(*) > 1;
SELECT role_id, menu_id, COUNT(*) FROM sys_role_menu GROUP BY role_id, menu_id HAVING COUNT(*) > 1;
SELECT ur.* FROM sys_user_role ur LEFT JOIN sys_user u ON u.user_id=ur.user_id
  LEFT JOIN sys_role r ON r.role_id=ur.role_id WHERE u.user_id IS NULL OR r.role_id IS NULL;
SELECT rm.* FROM sys_role_menu rm LEFT JOIN sys_role r ON r.role_id=rm.role_id
  LEFT JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE r.role_id IS NULL OR m.menu_id IS NULL;
SHOW INDEX FROM sys_user;
SHOW INDEX FROM sys_user_role;
SHOW INDEX FROM sys_role_menu;
SHOW INDEX FROM sys_menu;

-- 以下为待数据库维护人员审查的建议，仅在无同等索引且预检查完成后按需执行。
-- ALTER TABLE sys_user ADD UNIQUE INDEX uk_sys_user_username (username);
-- ALTER TABLE sys_user_role ADD UNIQUE INDEX uk_sys_user_role (user_id,role_id);
-- ALTER TABLE sys_user_role ADD INDEX idx_sys_user_role_role (role_id,user_id);
-- ALTER TABLE sys_role_menu ADD UNIQUE INDEX uk_sys_role_menu (role_id,menu_id);
-- ALTER TABLE sys_role_menu ADD INDEX idx_sys_role_menu_menu (menu_id,role_id);
-- ALTER TABLE sys_menu ADD INDEX idx_sys_menu_parent (parent_id);
-- 是否添加外键需结合存量数据、并发写入和运维窗口单独决定。
