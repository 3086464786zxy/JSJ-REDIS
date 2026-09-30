# JSJ-REDIS 管理后台脚手架

Java 21 / Spring Boot 3.5、MyBatis-Plus、MySQL 8.4、Redis 7.4，配套 Vue 3 / TypeScript / Element Plus 管理后台。包含账户、角色、菜单、数据库版本迁移和审计查询。

## 本地启动

1. 安装 Java 21、Maven、Node.js 24、MySQL 8.4 和 Redis 7.4。
2. 新项目创建空数据库，参考 `ops/database/create-database.sql`。已有数据库必须先执行 [升级预检查和备份流程](docs/RELIABILITY.md)，不能直接启用自动 baseline。
3. 配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`；JWT 密钥至少 32 字节且不能使用测试值。按环境设置 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE`。
4. `mvn -f '脚手架后端接口/itmk-base-parent/pom.xml' package -DskipTests`，然后运行 web 模块下 `target/itmk-base-web-1.0-SNAPSHOT.jar`。空库自动运行 Flyway V1–V5，不会生成默认密码。
5. 空库首次初始化管理员时设置 `APP_BOOTSTRAP_ENABLED=true`、`APP_BOOTSTRAP_USERNAME`、`APP_BOOTSTRAP_PASSWORD`；密码满足 12–64 字符、UTF-8 不超过 72 字节。初始化成功后移除这些变量并重启。已有用户时初始化开关会拒绝启动。
6. 在 `后台界面源码/project-web` 执行 `npm ci`、`npm run dev`。默认 API 端口 8090；监控端口 9091 仅绑定 127.0.0.1。

访问令牌 15 分钟，真实操作延长 30 分钟空闲会话，轮换刷新令牌使用 HttpOnly Cookie。没有连续登录时长上限。升级后旧会话需要重新登录。

## 验证与运维

- [安全改进说明](docs/HARDENING.md)
- [迁移、权限一致性、审计、告警、压测与恢复操作手册](docs/RELIABILITY.md)
- [隔离环境验证结果](docs/VALIDATION.md)

CI 使用真实 MySQL / Redis，执行后端测试、前端认证测试及构建、接口压测、备份恢复和告警规则验证。工作流不包含部署步骤。
