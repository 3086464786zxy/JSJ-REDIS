# 登录续期与闲置退出

默认连续闲置 30 分钟退出。有真实操作就延长 Redis 会话，没有最长登录时限。Access Token 默认 15 分钟到期，由已有双 Token 机制自动换发，页面无需重新登录。

## 行为

- 登录返回 JWT 和 `idleTimeoutSeconds`，Refresh Token 放在 HttpOnly Cookie 中。
- 页面可见时的鼠标、键盘、输入和触摸操作记录活动。业务请求携带活动标记；没有业务请求时，活动同步最多每分钟一次。后台轮询、自动刷新、重新加载页面不产生新的活动。
- JWT 到期的业务请求遇到业务码 600/401 或 HTTP 401 时，统一刷新并重试一次。同页请求共享一个刷新操作；支持 Web Locks 的浏览器在安全上下文中串行处理不同标签页的 Cookie 轮换。
- 刷新保留同一个会话 ID，原子检查会话仍存在、旧刷新凭证尚未消费，再换发 JWT 和 Refresh Token。刷新本身不延长闲置 TTL，也不创建会话。
- 无操作达到闲置时限，前端清空 Pinia 和持久化数据，撤销登录并跳转登录页。页面休眠后，过期会话不能靠第一次点击恢复；后端 Redis 会话到期后也不能刷新恢复。
- 同会话标签页通过活动时间同步闲置判断，不在 localStorage 共享 Token。临时网络或服务器故障不会主动清空有效登录。

## 配置与上线

后端 `app.security.session-idle-timeout-minutes` 默认 30；`jwt.expiration` 为 Access Token 有效分钟数，示例配置为 15；`jwt.refresh-expiration` 为刷新凭证每次换发后的有效分钟数，示例配置为 10080。7 天是单个刷新凭证的滚动有效期，不是最长登录时间。

活动同步有最多 60 秒的节流，Redis TTL 从服务器成功收到活动请求时计算；前端按实际最后操作时间及时退出。后端不依赖页面定时器判断已失效的会话。

前后端需要一起发布。Refresh Token 新格式为 `userId.UUID`，Redis 键使用同一个用户 hash tag，以便原子操作兼容 Redis Cluster；旧刷新凭证不再接受。旧客户端没有新的活动状态，因此更新后已登录用户需要重新登录一次。

前端接口统一使用单层 `/api/...`，开发代理和生产反向代理需要保留 `/api` 前缀。HTTPS 环境将 `app.security.refresh-token.cookie-secure` 配为 `true`，Cookie 的 Path 为 `/api/`。

## 验证

前端在 `后台界面源码/project-web` 运行：

```sh
npm ci
npm run test:auth
npm run build
```

后端在 `脚手架后端接口/itmk-base-parent` 使用 Java 21 运行：

```sh
mvn test
# 独立 Redis 实例可验证 Lua 原子操作，不会清空数据库
mvn -Dauth.redis.port=16389 test
```

Redis 集成测试使用随机用户键，测试后只清理自己创建的键；未指定端口时跳过。覆盖并发凭证轮换、活动续期、删除和闲置过期后不可恢复，以及早期登录时间不限制续期。单元测试覆盖刷新、过滤器、后台轮询不续期和 JWT 到期后通过 Cookie 退出。

Windows 的 JDK 若出现 `PipeImpl / UnixDomainSockets: Invalid argument: connect`，本机测试可令 Unix socket 临时路径指向不存在的目录，使 JDK 回退到 TCP：

```sh
mvn -Dauth.redis.port=16389 "-DargLine=-Djdk.net.unixdomain.tmpdir=D:/codex-auth-test-no-unix-socket -Djava.net.preferIPv4Stack=true" test
```
