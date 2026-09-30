-- 只读预检查。先备份、恢复验证并核对 V1 表定义，再明确 baseline 1。
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
SELECT user_id FROM sys_user WHERE username IS NULL OR password IS NULL
 OR is_enabled IS NULL OR is_account_non_expired IS NULL
 OR is_account_non_locked IS NULL OR is_credentials_non_expired IS NULL;
SELECT table_name,engine,table_collation FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_name IN ('sys_user','sys_role','sys_menu','sys_user_role','sys_role_menu');
SHOW CREATE TABLE sys_user;
SHOW CREATE TABLE sys_role;
SHOW CREATE TABLE sys_menu;
SHOW CREATE TABLE sys_user_role;
SHOW CREATE TABLE sys_role_menu;
-- 唯一约束、索引、外键由 V2 版本迁移创建，不要提前重复执行 ALTER。
-- 如果已有同名/等价约束或不兼容结构，先在恢复副本制定适配迁移方案。
