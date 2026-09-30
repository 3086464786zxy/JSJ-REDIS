# 可靠性与运维操作手册

适用于本仓库的管理后台/API。所有演练先在隔离环境执行；仓库工作流不会部署应用或修改生产数据库。

## 权限与会话一致性

`authz_state.version`、`sys_user.permission_version` 与角色/菜单/用户权限修改在同一个 MySQL 事务内更新。授权缓存键包含数据库版本；提交后旧 Redis 缓存即使仍存在，也不会被后续请求使用。读取权限时再次检查版本，避免并发回源使用跨版本结果；连续三次遇到变更则返回 503。

每个鉴权请求必须访问主库验证账户、会话撤销状态和当前权限版本。禁止把这些查询路由到延迟副本，也不能给查询添加二级缓存。权限缓存读写失败可以回源主库；数据库故障时拒绝授权，返回 503。Redis 会话/刷新令牌仍是必需依赖，Redis 整体不可用也返回 503。这一设计优先保证权限正确性，并增加主库查询量，必须按实际负载评估连接池和主库容量。

密码修改/重置递增数据库会话版本；禁用、删除、改名在下一次校验生效。退出将会话撤销写入数据库，Redis 清理失败不能使被撤销会话重新获得授权。已进入业务处理的请求不作强制中断；升级前会话缺少版本，需要重新登录。空闲 30 分钟的策略和没有连续登录上限的要求保持不变。

通过运维 SQL 修改授权数据时也必须在同一事务更新对应权限版本；修改密码/安全状态需要更新会话版本。绕过这些规则的数据库直写不受应用一致性机制保障。

## 数据库初始化与升级

迁移包括 V1 基础表、V2 唯一约束/外键/权限状态/审计、V3 初始菜单、V4 审计菜单、V5 审计详情及待完成请求。V3 只对空菜单表初始化，不覆盖既有菜单。脚本没有默认账户或密码。Flyway 禁止 clean、自动 baseline、乱序迁移；校验已执行脚本的校验和。上线后新增版本，不能改已执行的 V1–V5。

新安装：用 `ops/database/create-database.sql` 创建空库，使用迁移账户启动或执行下面的 `flyway:migrate`，然后通过一次性管理员初始化开关创建管理员。

已有库：

1. 停止所有写入，备份并在另一实例验证恢复。查询 `docs/database-hardening.sql`，检查重复用户名、重复关联、孤儿关联、NULL 安全字段、引擎和实际表定义。比较 V1：主键/关联字段类型和 signedness 必须一致，字段、默认值、排序规则及 InnoDB 必须兼容；检查是否已经有同名或等价约束。不要把这一步当成自动结构转换工具。
2. 使用业务规则明确修复脏数据并复查；迁移遇到重复/孤儿会失败，不自动删除数据。先在恢复副本跑一次完整升级。
3. 从仓库根目录执行 `mvn -f '脚手架后端接口/itmk-base-parent/pom.xml' install -DskipTests`，确保 Java 迁移已编译且模块依赖已安装。
4. 切换到 `脚手架后端接口/itmk-base-parent/itmk-base-web`，设置受保护环境变量 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。**仅经核实的旧基础库、且没有 Flyway 历史时**执行 `mvn flyway:baseline`（版本 1）；不要对空库或不兼容数据库 baseline。然后依次执行 `mvn flyway:migrate`、`mvn flyway:validate`、`mvn flyway:info`。插件扫描 classpath，同时包含 SQL 和 Java V3 迁移。
5. 使用新的运行账户启动应用，检查 readiness、管理员登录、角色授权、审计查询及普通用户拒绝访问。升级后所有客户端重新登录，再恢复流量。

MySQL DDL 可能部分提交。失败时保留日志和迁移历史，核查已经应用的 DDL；按已演练的恢复/修复方案处理，不能直接 `repair` 或重复 baseline 来掩盖错误。

生产建议将 DDL 权限与运行账户分开；独立迁移结束后设置 `SPRING_FLYWAY_ENABLED=false`。运行账户只保留所需表级权限：业务表 CRUD；`authz_state` SELECT/UPDATE；`auth_revoked_session` SELECT/INSERT/UPDATE；`sys_audit_event` SELECT/INSERT；`sys_audit_pending` SELECT/INSERT/DELETE。运行账户不能 UPDATE/DELETE 审计事件。初始化管理员开关只在首次空库使用，随后移除密码变量。

## 审计与异常对账

全部 `/api/**` 请求（包括查询、登录、刷新、拒绝访问）先持久化 RECEIVED，再持久化 COMPLETED；记录服务器生成请求 ID、用户/目标 ID、方法、路径、直接连接来源地址、UTC 时间、HTTP/业务结果及耗时。关键用户/角色/菜单写入额外记录 CHANGED，且与业务数据在同一个事务内提交。白名单详情保存角色 ID、菜单 ID、权限码和密码已修改标志；不保存密码、令牌、Cookie、完整请求体或查询串。反向代理部署时来源地址为代理地址，不盲目信任客户端 X-Forwarded-For。

审计接收失败时返回 503，业务不执行；CHANGED 写入失败使业务事务回滚。完成记录写入失败时保留 RECEIVED 和待完成状态，记录错误指标和请求 ID。超过五分钟未完成会使 readiness DOWN 并触发告警。后台审计查询仅超级管理员可读，没有修改/删除 API，分页最多 100 条。

发生告警后按请求 ID 检查 RECEIVED、CHANGED、COMPLETED 和应用日志。CHANGED 表示对应数据库写入已提交；缺失 COMPLETED 不等于业务没执行，不能盲目重放写操作。由受授权维护人员对账后追加说明事件、保留原始证据，再关闭对应待完成状态。不得通过删除审计记录消除告警。

审计需要容量/保留策略。当前每个查询有两次审计事务；按真实读写负载测量磁盘和数据库压力。由独立归档账户定期导出、校验并保存到受保护或不可改写存储，再按组织政策清理；应用运行账户无权清理。数据库管理员仍可改写数据库，此实现不等同于外部不可篡改存储。过期会话撤销记录可由独立维护任务按 `expires_at` 索引分批清理，不能删除尚未到期的记录。

## 监控与告警

Actuator 默认绑定独立 loopback 端口 9091，仅暴露 health 和 prometheus。readiness 同时检查主库、Redis 和待完成审计；health 不公开细节。管理端口应继续保留在受保护网络，不经公网代理转发。多机采集时明确配置私网监听和防火墙，不能直接改成公开监听。

`ops/monitoring/prometheus.yml`、`blackbox.yml`、`alerts.yml` 包含指标不可用、readiness 失败、探针不可用、HTTP 5xx、p95 延迟、审计写入失败/缺失完成、权限缓存故障、连接池压力/超时共 10 条规则。部署监控时启动 Prometheus、blackbox exporter 和 Alertmanager，按实际监听地址修改目标，配置组织自己的 Alertmanager 通知接收方、值班路由及抑制规则。没有在仓库保存真实通知地址或凭据。

验证命令：`promtool check config ops/monitoring/prometheus.yml`、`promtool test rules ops/monitoring/alert-tests.yml`。隔离环境可停止依赖、制造待完成记录或发送测试告警，确认规则触发、恢复和接收方实际收到通知；本次已验证规则和端点，没有向外部接收方发送通知。接收方的端到端送达仍需在真实运维环境验收。

## 压力测试

在预发布环境使用单独测试账户登录，设置 `LOAD_BASE_URL` 和 `LOAD_TOKEN`，执行 `node ops/load/http-load.mjs`，或执行 `k6 run ops/load/k6.js`。默认预算：p95 < 500 ms、错误率 ≤ 1%；接口同时验证 HTTP 和业务 code。Node 默认 10 并发、30 秒，可配置 `LOAD_VUS`、`LOAD_SECONDS`、`LOAD_P95_MS`、`LOAD_ERROR_RATE`、`LOAD_REPORT`。k6 使用带停顿的递增负载，两种模型的吞吐量不能直接比较。

只访问用户信息、菜单和用户分页，不写业务数据，但会产生审计数据。Node 是封闭并发模型且没有思考时间；没有请求到达率保证，不能单靠它确认突发流量容量。当前脚本使用一次登录的 JWT，单次测试须小于 15 分钟；更长测试需要增加真实刷新流程，不能关掉鉴权。令牌放临时受保护环境，禁止提交到 Git 或记录在报告。结合生产数据规模、授权关系、CPU/GC、数据库连接/慢查询、审计磁盘、Redis 延迟和故障恢复做容量验收。

CI 的 `ops/validation/ci-runtime.mjs` 仅支持 loopback 隔离测试库，通过测试装置预置一次性验证码，走真实登录接口；产品没有增加验证码或密码绕过。CI 启动临时应用、验证管理端口隔离、实际压测与审计查询，结束后停止进程。

## 备份与恢复

需要匹配服务端的 MySQL 8.4 `mysql`、`mysqldump` 及 Node 24。将连接参数写入受保护 option file，例如 `[client]` 下的 host、port、user、password；设置 `MYSQL_DEFAULTS_FILE` 指向它，必要时配置 `MYSQL_BIN`、`MYSQLDUMP_BIN`。密码不进入命令行。Windows 需用目录/文件 NTFS ACL 保护备份，Node 的 POSIX mode 不能代替 ACL。

1. `node ops/database/backup-restore.mjs backup itmk /protected/itmk-unique.sql`。文件和校验文件都拒绝覆盖已有文件。使用 InnoDB 一致性快照、按主键导出，包含触发器/例程/事件；备份期间禁止 DDL。失败的部分文件没有有效校验文件，不能恢复。
2. `node ops/database/backup-restore.mjs restore-check jsj_restore_drill /protected/itmk-unique.sql`。目标必须是不存在的 `jsj_restore_*` 数据库；脚本不 DROP 数据库，不允许 dump 包含 USE/CREATE DATABASE/DROP DATABASE。只使用可信的本工具备份，不执行来源不明的 SQL。
3. 恢复前校验原始 SHA-256；恢复后重新导出并比较全部结构/数据的规范化 SHA-256，再检查迁移历史和 4 个外键。仅规范化换行及列 DDL 中由排序规则决定的冗余 CHARACTER SET；不跳过数据、索引或约束。出现差异保留恢复库和两个受保护导出文件调查。
4. 用恢复库启动隔离应用，确认 Flyway 校验、readiness、重新登录、查询与权限拒绝都正确。恢复生产流量之前轮换 JWT 密钥，并使用全新的专用 Redis database/实例清空会话空间，**不能复用数据库回滚前的 Redis 缓存版本和会话**。所有用户重新登录。
5. 记录数据规模、备份/恢复/应用恢复时间；恢复演练数据库及备份含敏感数据，验收后通过经确认的维护流程处理。脚本故意不自动删除证据。

本工具校验逻辑备份恢复，不包含异地存储、加密、定时执行、binlog 连续归档/PITR、MySQL/Redis 高可用和告警通知接收配置。实际环境需按批准的 RPO/RTO 配置备份频率、异地/不可改写副本、保留期限、密钥管理和定期演练；不能用本机小测试库的恢复时间估计生产 RTO。

## 参考

- [Spring Boot 数据库初始化](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)
- [Actuator 端点](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- [Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline)
- [MySQL 8.4 mysqldump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)
- [k6 阈值](https://grafana.com/docs/k6/latest/using-k6/thresholds/)
