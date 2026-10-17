# RBAC 权限管理后台

基于 **Spring Boot + Spring Security + MySQL + Redis + RocketMQ + Vue 3** 的前后端分离 RBAC 权限管理系统，内置独立的 **OAuth2 授权服务器**供第三方系统接入，并附两个**已接入的独立子系统**（订单中心、营销系统）演示「认证与授权统一交给中枢、业务系统自己完全不碰用户与权限」。

## 核心能力

| 需求 | 实现方式 |
|---|---|
| **无感知续期** | Access Token 短有效期（默认 15 分钟），过期后前端拦截 401，用 Refresh Token 自动调用 `POST /auth/refresh` 换取新的 Access Token 并重放原请求（并发 401 只刷新一次）。用户全程无感知，不需要重新登录。 |
| **离职立刻失效凭证** | 用户状态三值：**1 在职 / 0 停用 / 2 离职**。会话保存在 Redis（`login:session:{userId}` + `login:refresh:{sessionId}`）。标记停用或离职时删除会话键，配合认证过滤器的会话校验，存量 Access Token 在下一次请求即被拒绝，旧 Refresh Token 也无法再续期，无需等待自然过期。 |
| **登录快照（用户 + 权限 + 角色）** | 用户信息、权限、角色在**登录时**查库一次，整体序列化写入 Redis（`login:perm:{sessionId}`）。之后每个请求都由快照重建认证主体，**不再访问数据库** —— 省掉「用户-角色-菜单」的多表联查与逐请求的用户行查询。 |
| **动态修改角色权限** | 管理员在角色授权页调整「角色-菜单」映射后，服务端立即**失效该角色下所有用户的权限快照**，用户下一次请求重建快照并拿到新权限，无需重新登录。 |
| **子系统菜单统一配置** | RBAC 作为中枢登记各子系统，并维护每个子系统自己的菜单树（目录 / 菜单 / 按钮三级，三者都带 `perms` 权限标识）；角色在「角色管理」里按子系统分配可见菜单。数据落在 `sys_role_subsystem_menu`，并**经 RocketMQ 实时下发给对应子系统** —— 已在运行的子系统无需重启、重新登录即可生效，见「子系统接入」一节。 |
| **超级管理员免配置** | `admin` 的权限**不落库**：代码里直接给通配符 `*:*:*` 与全部启用菜单。新增菜单或接口后不需要再手动给它补授权，`sys_role_menu` 里也不会有它的记录。 |
| **操作日志** | 全部落库到 `sys_operation_log`：登录、登出、强制下线、停用/启用/离职、重置密码、自助改密，以及用户/角色/菜单/部门/岗位的**新增、修改、删除、授权**。写入类操作由 `@OperLog` 注解 + AOP 切面自动记录（含模块、说明、请求方法路径、成败、耗时、来源 IP），查询类接口不记，避免日志表被翻页请求淹没。管理端「操作日志」页可按用户名、模块、动作、结果、时间段检索。 |
| **单点登录与统一登出** | **统一门户**（5177）一次登录，可一键给所有子系统签发令牌；子系统自己没有登录页，未登录自动跳门户、登录完自动回来。任一入口登出即删掉该用户的 SSO 会话：所有子系统的旧令牌**下一个请求即失效**，刷新令牌也换不出新的（自包含 JWT 本身删不掉，靠这层服务端会话把生死收回来）。管理端可查看在线用户并强制下线。见「统一门户 SSO」一节。 |

## 服务划分

授权相关代码已从 RBAC 后台拆出，另有**两个已接入的独立子系统**（订单中心、营销系统），再加一个**统一门户**演示「RBAC 作为统一认证与授权中枢，业务系统自己完全不碰登录与权限管理」：

| 服务 | 端口 | 类型 | 职责 |
|---|---|---|---|
| **rbac-backend** | 7070 | 中枢 | RBAC 业务：登录/续期、用户、角色、菜单、部门、岗位、客户端管理、子系统与子系统菜单、操作日志、**SSO 会话管理**、**门户接口 `/portal/**`** |
| **rbac-oauth** | 7071 | 中枢 | OAuth2 授权服务器：五种授权模式的端点、**统一登出 `/oauth/sso-logout`**、第三方开放接口 `/openapi/**` |
| **rbac-frontend** | 5173 | 中枢 | RBAC 管理后台界面 |
| **rbac-oauth-web** | 5174 | 中枢 | OAuth2 授权流程页面：授权登录页、授权确认页。**刻意与门户分开**：它的登录页要用原生表单 POST 到 `/oauth/login`，必须与授权服务同源部署（靠 `/oauth → 7071` 代理），而门户是业务应用、不需要这个约束，也不该被它绑住 |
| **portal-frontend** | 5177 | 中枢 | **统一门户**：一次登录 → 一键给所有子系统签发令牌 → 统一登出 |
| **order-backend** | 7073 | 子系统 | 订单中心后端：订单增删改查 + 统计汇总。独立库 `order_center`，无登录、无用户表 |
| **order-frontend** | 5176 | 子系统 | 订单中心界面：登录入口跳统一门户（无自己的登录页）、动态路由、按钮级权限 |
| **marketing-backend** | 7072 | 子系统 | 营销系统后端：抽奖活动、奖品、积分任务、流水与记录。独立库 `marketing` |
| **marketing-frontend** | 5175 | 子系统 | 营销系统界面（结构同 order-frontend） |

两个中枢后端**共用同一个 MySQL 库与同一套 Redis**，配置上有几项必须一致，见文末「拆分后的共享约定」。

子系统之间、以及子系统与中枢之间**没有共用表、没有外键、没有跨库查询**：每个子系统一个独立库，登录与权限全部来自 RBAC，业务数据全部属于自己。

## 技术栈

- **后端**：Spring Boot 2.7.18 / JDK 8 / Spring Security 5.7 / MyBatis-Plus 3.5 / jjwt 0.11.5 / Maven
  - rbac-oauth 另用 spring-security-oauth2 2.5.2（传统授权服务器）+ spring-security-jwt（JWT 令牌）
  - 两个子系统的后端另用 RocketMQ Spring Boot Starter 2.2.3（消费 RBAC 下发的权限数据）
- **存储**：MySQL 8.0（业务数据，中枢与各子系统各自独立的库）+ Redis（登录会话 + OAuth2 授权流程的会话/跳转地址）
- **消息**：RocketMQ（中枢 → 子系统的权限下发通道，topic `RBAC_SUBSYSTEM_SYNC`）
- **前端**：Vue 3 / Vite 5 / Element Plus / Vue Router 4 / Pinia / axios

## 目录结构

```
springsecurity-RBAC/
├── rbac-backend/          # ① RBAC 后端（7070）
│   └── src/main/java/com/example/rbac/
│       ├── common/        # 统一返回、异常、状态码
│       ├── config/        # Security / Redis / MyBatis-Plus 配置
│       ├── aspect/        # @OperLog 操作日志切面
│       ├── security/      # JWT、会话存储、权限快照、权限校验、超级管理员隐式权限、过滤器
│       ├── entity/        # 各表实体（含 sys_subsystem / sys_subsystem_menu）
│       ├── mapper/        # MyBatis-Plus Mapper
│       ├── service/       # 业务逻辑
│       ├── controller/    # 接口
│       ├── dto/           # 请求/响应对象
│       └── util/          # 菜单树工具
├── rbac-oauth/            # ② OAuth2 授权服务器（7071）
│   └── src/main/
│       ├── java/com/example/oauth/
│       │   ├── config/    # 四条安全链（令牌端点/授权端点/开放接口/兜底）+ 令牌签名
│       │   ├── oauth/     # 客户端密钥解密、客户端加载、确认页数据、登录入口点
│       │   ├── controller/# 第三方开放接口 /openapi/**
│       │   ├── entity/    # 本服务用到的表：clientdetails（只读）、sys_user
│       │   ├── mapper/    # 对应的 Mapper
│       │   ├── security/  # 用户身份加载、会话校验与清理
│       │   ├── service/   # 开放接口的用户访问
│       │   ├── dto/       # 开放接口用户视图
│       │   └── common/    # 统一返回与异常（与 rbac-backend 同一套约定）
│       └── resources/
│           ├── application.yml
│           └── jwt.jks    # OAuth2 令牌的 RSA 签名密钥对
├── rbac-frontend/         # ③ RBAC 管理后台前端（5173）
│   └── src/
│       ├── api/           # axios 实例（含无感刷新）与各模块接口
│       ├── store/         # Pinia：用户信息、动态路由
│       ├── router/        # 静态路由 + 路由守卫 + 动态 addRoute
│       ├── layout/        # 后台布局与递归菜单
│       └── views/         # 登录、仪表盘、用户/角色/菜单/部门/岗位/客户端/操作日志
├── rbac-oauth-web/        # ④ OAuth2 授权流程页面（5174）
│   └── src/
│       ├── router/        # /login、/approve 两个页面
│       └── views/         # 授权登录页（表单登录）、授权确认页
├── order-backend/         # ⑤ 订单中心后端（7073，独立库 order_center）
│   └── src/main/java/com/example/order/
│       ├── common/        # 统一返回、异常、状态码
│       ├── config/        # Security / MyBatis-Plus / Jackson / OAuth2 客户端配置
│       ├── security/      # 令牌换用户名、权限判定（@ss）、超级管理员识别、401/403 处理器
│       ├── entity/        # order_user / order_menu / order_role_menu / order_user_role + order_info
│       ├── mapper/        # MyBatis-Plus Mapper（含「按用户查权限点」的联查）
│       ├── service/       # 登录授权、MQ 快照重建、订单业务
│       ├── controller/    # /auth/*、/order/*
│       ├── mq/            # 消费 RBAC 下发的子系统快照（tag = order-center）
│       └── dto/ util/
├── order-frontend/        # ⑥ 订单中心界面（5176）—— /api 代理到 7073
│   └── src/
│       ├── api/           # axios 实例（含 401 单飞刷新）与订单接口
│       ├── store/         # Pinia：用户信息、动态路由
│       ├── utils/         # 令牌存取、统一登出（sso.js）、切回标签页校验登录态（sessionWatch.js）
│       ├── views/         # 登录跳转页（不是表单）、授权码回调、仪表盘、order/list、order/stat
│       └── layout/ directive/
├── marketing-backend/     # ⑦ 营销系统后端（7072，独立库 marketing，结构与 order-backend 相同）
├── marketing-frontend/    # ⑧ 营销系统界面（5175）—— /api 代理到 7072
├── portal-frontend/       # ⑨ 统一门户界面（5177）—— /api 代理到 rbac-backend 的 /portal/**
│   └── src/
│       ├── api/           # 门户接口：sso-login（302 跳转）/ token / subsystems / info
│       ├── store/         # Pinia：门户令牌 + 已登录的子系统（clients）
│       ├── utils/         # 门户令牌存取、统一登出、切回标签页校验登录态
│       └── views/         # 登录、授权码回调、工作台（子系统卡片 + 一键登录）
└── sql/
    ├── schema.sql                             # 建表脚本（含 clientdetails）
    ├── data.sql                               # 初始化数据
    ├── upgrade-2026-10-13-dept.sql            # 旧库升级①：部门模块
    ├── upgrade-2026-10-13-company.sql         # 旧库升级②：分公司模块（已被③合并回组织树）
    ├── upgrade-2026-10-13-org-post.sql        # 旧库升级③：分公司并入部门树 + 岗位模块
    ├── upgrade-2026-10-13-audit.sql           # 旧库升级④：登录/下线审计日志
    ├── upgrade-2026-10-13-operlog.sql         # 旧库升级⑤：审计日志扩展为通用操作日志（表改名）
    ├── upgrade-2026-10-13-release-unique-key.sql  # 数据修复：释放被逻辑删除行占用的唯一键
    ├── upgrade-2026-10-14-client.sql          # OAuth2 客户端配置表 + 示例客户端
    ├── upgrade-2026-10-16-oauth-server.sql    # 授权服务器拆出后：修正示例客户端的回调地址端口
    ├── upgrade-2026-10-16-subsystem.sql       # 子系统菜单权限：3 张表 + 菜单种子 + 示例子系统
    ├── upgrade-2026-10-16-super-admin.sql     # 超级管理员改为代码隐式授予：清掉其 sys_role_menu 记录
    ├── upgrade-2026-10-16-marketing.sql       # 营销系统接入中枢：客户端 + 菜单/按钮权限点 + 角色授权
    ├── upgrade-2026-10-16-order-center.sql    # 订单中心接入中枢：客户端 + 按钮权限点 + 去掉客户菜单 + 角色授权
    ├── upgrade-2026-10-16-sso.sql             # 统一门户 SSO：portal 客户端 + 子系统客户端免确认页 + SSO 会话菜单与权限点
    ├── order-schema.sql                       # 订单中心独立库 order_center（同步表 + order_info + 演示数据）
    ├── marketing-schema.sql                   # 营销系统独立库 marketing
    └── marketing-sync-manual.sql              # 手工灌一次同步数据（仅 RocketMQ 未启用时联调用）
```


## 快速开始

### 1. 准备数据库与 Redis

全新初始化（Windows 下务必带 `--default-character-set=utf8mb4`，否则中文会因客户端字符集报 `Data too long` 假错）：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql          # 中枢库 rbac
mysql -uroot -p --default-character-set=utf8mb4 < sql/data.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/order-schema.sql    # 订单中心库 order_center
mysql -uroot -p --default-character-set=utf8mb4 < sql/marketing-schema.sql # 营销系统库 marketing
```

已有的旧库按文件名顺序执行增量脚本升级（脚本一次性执行，重复执行会因 `ALTER` / 主键冲突而报错）：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-dept.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-company.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-org-post.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-audit.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-operlog.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-release-unique-key.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-14-client.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-16-oauth-server.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-16-subsystem.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-16-super-admin.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-16-marketing.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-16-order-center.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-16-sso.sql
```

> 前几个脚本是迭代过程留下的：先是独立部门表，再加独立分公司模块，最后把分公司并入部门树、换成岗位模块。中途的 `company` 脚本只挂载了一次独立分公司模块，随后被最后一个脚本合并回组织树并删除。**全新安装只需执行 `schema.sql` + `data.sql` + 两个子系统的 schema。**
>
> 两个子系统的 `*-schema.sql` 都是 `DROP DATABASE` 重建，只在首次接入时执行；日常升级不碰它们（同步表的数据由 MQ 自己维护）。

Redis 默认连接 `localhost:6379`。子系统**不使用 Redis**。

### 2. 准备 RocketMQ

子系统靠 RocketMQ 接收 RBAC 下发的权限数据，需要先启动 NameServer（9876）与 Broker（10911）：

```bash
mqnamesrv                                   # NameServer，默认 9876
mqbroker -n localhost:9876                  # Broker
```

topic `RBAC_SUBSYSTEM_SYNC` 由生产者自动创建，不需要手工建。

> **Broker 没起来会怎样**：中枢照常运行，子系统也照常启动，但同步表是空的 —— 表现为「授权服务能认证通过，但本系统提示账号未同步（2004）」。这是预期行为：子系统的权限数据**只**来自 MQ。

### 3. 修改后端配置

四个后端的 `application.yml` 都要改，**其中几项中枢两服务必须一致**：

- `rbac-backend/src/main/resources/application.yml`
- `rbac-oauth/src/main/resources/application.yml`
- `order-backend/src/main/resources/application.yml`
- `marketing-backend/src/main/resources/application.yml`

| 配置项 | 说明 |
|---|---|
| `oauth2.client-secret-key` | **中枢两服务必须相同**（子系统不需要）。rbac-backend 用它加密/解密客户端密钥（页面要展示明文给第三方），rbac-oauth 用它解密以校验客户端身份。一旦变更，库中已存的密文都解不开，需在「客户端管理」页面重设密钥 |
| MySQL 连接 | 中枢两服务是同一个库：`clientdetails` 由 rbac-backend 读写、rbac-oauth 读；`sys_user` 供授权服务的密码模式校验身份与开放接口的用户访问。**子系统各连各的库**（`order_center` / `marketing`），与中枢库无任何交集 |
| Redis 连接 | 同一套 Redis（库号也要一致）：`login:session:*` 由 rbac-backend 写、rbac-oauth 读。子系统不连 Redis |
| `oauth2.client-id` / `client-secret` | 子系统各自的客户端身份，必须与中枢库 `clientdetails` 里的记录一致（订单中心 `order-center` / `order123`，营销系统 `marketing` / `marketing123`） |
| `oauth2.redirect-uri` | 子系统前端的回调页，必须与 `clientdetails.redirectUrl` **一模一样**，否则授权码模式换令牌会被拒（订单中心 5176，营销系统 5175） |
| `oauth2.portal.*` | **仅 rbac-backend**：统一门户的客户端身份与回调地址，必须与 `clientdetails` 里 `portal` 客户端一致（5177 / `portal123`） |
| 前端 `.env.development` | 三个前端各要配 `VITE_OAUTH_SERVER_URL`（授权服务 7071）：子系统未登录时跳它去登录、统一登出也整页跳向它；门户的登录同理。改端口/换授权服务地址时改这一项即可 |
| `rocketmq.name-server` | 子系统必填（`localhost:9876`）。中枢的 rbac-backend 也用它做**生产者** |

`jwt.secret` 生产环境务必更换，长度 ≥ 32 字节。

### 4. 启动四个后端

```bash
cd rbac-backend      && mvn spring-boot:run     # RBAC 业务：http://localhost:7070
cd rbac-oauth        && mvn spring-boot:run     # OAuth2 授权服务器：http://localhost:7071
cd order-backend  
   && mvn spring-boot:run     # 订单中心：http://localhost:7073
cd marketing-backend && mvn spring-boot:run     # 营销系统：http://localhost:7072
```

中枢两个无启动顺序要求（各自独立连库与 Redis）。**子系统要先等中枢起来、并且 RBAC 推过一次权限数据**，否则同步表为空、登录会提示账号未同步（2004）—— 让 RBAC 推一次最简单的办法是重启 rbac-backend（启动时全量下发一次），或在「子系统菜单管理」页面点一下保存。

### 5. 启动五个前端

```bash
cd rbac-frontend      && npm install && npm run dev    # 管理后台：http://localhost:5173
cd rbac-oauth-web     && npm install && npm run dev    # 授权流程页：http://localhost:5174
cd portal-frontend    && npm install && npm run dev    # 统一门户：http://localhost:5177
cd order-frontend     && npm install && npm run dev    # 订单中心：http://localhost:5176
cd marketing-frontend && npm install && npm run dev    # 营销系统：http://localhost:5175
```

- 管理后台 `http://localhost:5173`：`/api` 代理到 rbac-backend（7070）
- 授权流程页 `http://localhost:5174`：`/oauth` 原样代理到 rbac-oauth（7071）——授权码/隐式模式靠浏览器跳转与原生表单提交，必须与授权端点同源，会话 Cookie 才带得上
- 统一门户 `http://localhost:5177`：`/api` 代理到 rbac-backend（7070）的 `/portal/**`。**它是所有子系统的统一入口**
- 订单中心 `http://localhost:5176`：`/api` 代理到 order-backend（7073）。**没有自己的登录页**，未登录会直接去授权服务的登录页（5174），登录完自动回到本系统
- 营销系统 `http://localhost:5175`：`/api` 代理到 marketing-backend（7072）。同上

> 令牌存在 `localStorage`，每个前端用自己的键名（`rbac_*` / `order_*` / `marketing_*`），因此**三个前端可以同时开在同一个浏览器里互不干扰**。登录态则由 rbac-oauth 的会话 Cookie 共享：登录过一次之后，再访问另一个子系统会**跳过登录页、也跳过授权确认页**（两个子系统客户端的 `autoApproveScopes` 已放行 `read`），直接落地。

### 6. 默认账号

| 账号 | 密码 | 角色 | RBAC 后台 | 子系统（订单中心 / 营销系统） |
|---|---|---|---|---|
| admin | admin123 | 超级管理员 | 全部权限 | 全部功能 |
| zhangsan | admin123 | 普通用户 | 仅仪表盘 + 用户列表查询 | 两条只读页面（订单列表/订单统计、抽奖记录/积分流水），无任何写按钮 |

账号密码只有一套，存在中枢库 `sys_user` 里；子系统没有自己的用户表，登录时密码由授权服务器校验。

## 接口一览

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 认证 | POST | `/auth/login` | 登录，返回 Access Token + Refresh Token |
| 认证 | POST | `/auth/refresh` | 用 Refresh Token 换取新的 Access Token（Access 过期时前端自动调用） |
| 认证 | POST | `/auth/logout` | 登出，删除会话 |
| 认证 | GET | `/auth/info` | 当前用户信息 + 权限 + 菜单树 |
| 认证 | DELETE | `/auth/kick/{userId}` | 强制用户下线（`system:user:edit`；禁止踢自己 1019，目标不存在 1003） |
| 认证 | PUT | `/auth/password` | 自助修改密码（校验原密码，**保留当前会话**，不会把自己踢下线） |
| 用户 | GET | `/user/page` | 分页查询（`system:user:list`） |
| 用户 | POST/PUT/DELETE | `/user`、`/user/{id}` | 增改删 |
| 用户 | PUT | `/user/{id}/status` | 设置状态：1 在职 / 0 停用 / 2 离职（停用与离职都立即失效登录凭证） |
| 用户 | PUT | `/user/{id}/roles` | 分配角色 |
| 用户 | PUT | `/user/{id}/resetPassword` | 管理员重置他人密码（**强制该用户下线**，需用新密码重新登录） |
| 角色 | GET | `/role/page`、`/role/all` | 分页 / 全部启用角色 |
| 角色 | PUT | `/role/{id}/menus` | **动态修改角色权限**（超级管理员角色返回 1028：其权限由代码隐式授予） |
| 部门 | GET | `/dept/tree` | 组织架构树：公司 / 分公司 / 部门（type 1/2/3），管理页与用户表单共用 |
| 部门 | POST/PUT/DELETE | `/dept`、`/dept/{id}` | 增改删（删除前校验下级节点与关联用户） |
| 岗位 | GET | `/post/page`、`/post/list` | 分页 / 全部启用岗位 |
| 岗位 | POST/PUT/DELETE | `/post`、`/post/{id}` | 增改删（删除前校验是否已被用户使用） |
| 菜单 | GET | `/menu/tree` | 菜单树 |
| 菜单 | POST/PUT/DELETE | `/menu`、`/menu/{id}` | 增改删 |
| 审计 | GET | `/audit/page` | 操作日志分页查询（`system:audit:list`），支持用户名 / 模块 / 动作 / 结果 / 时间段 |
| 客户端 | GET | `/client/page`、`/client/{appId}` | 客户端列表 / 详情（`system:client:list` / `system:client:query`） |
| 客户端 | POST/PUT/DELETE | `/client`、`/client/{appId}` | 客户端增改删（`system:client:add` / `edit` / `remove`，均写操作日志） |
| 子系统 | GET | `/subsystem/list`、`/subsystem/page` | 启用子系统列表（下拉用）/ 分页（`system:subsystem:list`） |
| 子系统 | POST/PUT/DELETE | `/subsystem`、`/subsystem/{id}` | 子系统增改删（编码唯一 1025；删除前校验其下无菜单 1027；均写操作日志） |
| 子系统菜单 | GET | `/subsystem-menu/tree?subsystemId=` | 指定子系统的菜单树（`system:subsystem:menu:list`，角色授权树也可调） |
| 子系统菜单 | POST/PUT/DELETE | `/subsystem-menu`、`/subsystem-menu/{id}` | 增改删（目录 / 菜单 / 按钮三级，按钮节点带 `perms` 权限点；删除前校验无子节点 1011）。增删改都会触发一次该子系统的 MQ 下发 |
| 角色 | GET | `/role/{id}/subsystem-menus?subsystemId=` | 角色在该子系统下已分配的菜单 ID（授权页回显） |
| 角色 | PUT | `/role/{id}/subsystem-menus` | 分配角色的子系统菜单（按子系统**局部替换**，写操作日志；改完立即触发该子系统的 MQ 下发） |

接口鉴权使用 `@PreAuthorize("@ss.hasPerm('system:user:list')")`，权限来自用户所属角色的菜单权限标识。

### OAuth2 授权服务器（rbac-oauth，`http://localhost:7071`）

| 端点 | 方法 | 说明 |
|---|---|---|
| `/oauth/authorize` | GET/POST | 授权端点（授权码 / 隐式模式）；确认页把审批结果 POST 回这里 |
| `/oauth/login` | POST | 授权流程的表单登录（原生表单提交，登录态落在 HTTP 会话里，不是 JWT） |
| `/oauth/token` | GET/POST | 令牌端点，需先通过客户端认证（HTTP Basic，或表单参数 client_id/client_secret） |
| `/oauth/check_token` | GET/POST | 远程校验令牌，对外开放 |
| `/oauth/token_key` | GET | 令牌签名公钥，对外开放，供第三方本地验签 |
| `/oauth/approval-info` | GET | 待授权请求的信息（clientId / scopes / state），供确认页渲染 |
| `/oauth/sso-logout` | GET/POST | **统一登出**：删 SSO 会话（各子系统令牌即刻失效）+ 销毁浏览器会话。GET 版供整页跳转，302 回 `redirect_uri`（仅限已登记客户端的来源）；POST 版返回 JSON |
| `/oauth/test-callback` | GET | 授权码回调联调端点，**仅测试用**（`oauth2.test-callback.enabled=false` 可关闭） |
| `/openapi/userinfo` | GET | 令牌自检：返回该令牌的 subject / clientId / scopes |
| `/openapi/users` | GET | 用户列表，需要 scope `read` |
| `/openapi/users/{id}/status` | PUT | 修改用户状态，需要 scope `write` |

除了 `/openapi/**` 按 OAuth2 令牌的 scope 判权，本服务其余端点或对外开放（`/oauth/token_key`、`/oauth/check_token`），或走浏览器会话（授权流程）。**客户端管理不在这里**——它是后台管理功能，跑在 rbac-backend 上，按 `system:client:*` 权限点判权。

已登记的两个子系统客户端（密钥在库里是 AES 密文，这里是明文）：

| appId | 明文密钥 | grantTypes | redirectUrl | scope |
|---|---|---|---|---|
| `order-center` | `order123` | authorization_code, password, refresh_token | http://localhost:5176/oauth/callback | read,write |
| `marketing` | `marketing123` | authorization_code, password, refresh_token | http://localhost:5175/oauth/callback | read,write |

> 子系统**只申请 `read` 这一个 scope**（见各自 `application.yml` 的 `oauth2.scope`）：令牌端点的 `scope` 按 RFC 6749 是以**空格**分隔的，写成 `read,write` 会被当成一个名叫 `read,write` 的范围而报 `invalid_scope`。子系统不按 scope 判权，用的是 RBAC 下发的权限点，所以申请一个 `read` 就够。

### 子系统后端（order-backend `:7073` / marketing-backend `:7072`）

两个子系统的接口形状完全一致，把权限点前缀换掉即可（订单中心 `order:*`，营销系统 `marketing:*`）。以订单中心为例：

| 模块 | 方法 | 路径 | 权限点 |
|---|---|---|---|
| 登录 | POST | `/auth/login` | 无（白名单）：密码模式，账号密码转交 rbac-oauth 校验 |
| 登录 | POST | `/auth/code` | 无（白名单）：授权码模式，用回调带回来的 code 换令牌 |
| 登录 | GET | `/auth/sso-login` | 无（白名单）：拼接授权地址并 **302** 过去（前端只导航，拿不到 client_secret） |
| 登录 | POST | `/auth/refresh` | 无（白名单）：刷新令牌 |
| 登录 | GET | `/auth/info` | 需登录：返回身份 + 角色 + 权限点 + 菜单树 |
| 订单 | GET | `/order/page` | `order:order:list` |
| 订单 | GET | `/order/{id}` | `order:order:query` |
| 订单 | POST/PUT | `/order` | `order:order:add` / `order:order:edit` |
| 订单 | DELETE | `/order/{id}` | `order:order:remove` |
| 订单 | GET | `/order/stat` | `order:stat:list`（只读统计：总数、金额合计、按状态分布） |

子系统**没有登录页的账号体系、也没有用户表**：`/auth/*` 只是把请求转给授权服务器，落库的只有从 RBAC 同步来的用户/菜单/角色数据。

## 核心功能验证步骤

### 1. 无感知续期

Access Token 有效期短（默认 15 分钟），过期后前端拦截 401，自动用 Refresh Token 换新并重放原请求，用户无感知。

1. 用 admin 登录，打开开发者工具 Network，此时 `localStorage` 中会有 `rbac_access_token` 与 `rbac_refresh_token`。
2. 等 Access Token 过期后点击任意菜单：该请求先返回 401，紧接着自动发起一次 `POST /auth/refresh`，随后原请求被重放并成功——页面无跳转、无提示。
3. 并发多个请求同时 401 时只会触发一次刷新（单例 Promise 队列）。

> 快速验证（不必等 15 分钟）：
> ```bash
> mvn spring-boot:run -Dspring-boot.run.arguments=--jwt.access-token-expire=30000
> ```
> 登录后等 30 秒再点菜单，Network 中即可看到 401 → `/auth/refresh` → 重放 的完整链路。

### 2. 离职立刻失效

1. 用 zhangsan 在浏览器 A 登录，能正常访问仪表盘。
2. 用 admin 在浏览器 B 的「用户管理」中点击 zhangsan 的**离职**（也可点**停用**）。
3. 回到浏览器 A 点击任意菜单，请求立即返回 401 并跳转登录页——无需等 Token 过期。
4. 即使拿旧的 refreshToken 调 `/auth/refresh` 也会被拒绝（会话已删除）。
5. zhangsan 再尝试登录，会分别提示「该用户已离职」或「账号已停用」；把状态改回**在职**后即可重新登录。

### 3. 动态修改角色权限

1. 用 admin 在「角色管理」中点击「普通用户」的**分配权限**。
2. 勾选「用户管理」下的「用户新增」等按钮权限，保存。
3. 让 zhangsan 刷新页面：侧边栏与按钮立即出现新增入口；调用相应接口不再返回 403。
4. 反向操作（取消勾选）同样立即生效，无需 zhangsan 重新登录。

### 4. 操作日志

**登录/下线部分**

1. 用 zhangsan 登录、登出各一次；再用 admin 对 zhangsan 依次执行「强制下线（`DELETE /auth/kick/2`）」「停用」「启用」「离职」「重置密码」。
2. 用 admin 打开「系统管理 → 操作日志」，上述动作各有记录：目标用户 = zhangsan，`KICK/DISABLED/RESIGNED/RESET_PWD` 的操作人是 admin，`LOGIN/LOGOUT` 的操作人是 zhangsan 本人。

**增删改部分**

3. 用 admin 新增一个岗位，再改一次、删掉；随后故意用重复编码再新增一次。
4. 回到操作日志页，能看到「岗位管理 / 新增岗位 / 修改岗位 / 删除岗位」三条成功记录，以及一条 `结果 = 失败` 的记录，其失败原因是「岗位编码已存在」。
5. 各条都带请求方法路径（`POST /post`）、耗时、来源 IP 与操作人。
6. 反复点击列表翻页，日志条数**不会增加** —— 查询类接口不记录。

**筛选与权限**

7. 按模块 `岗位管理`、动作 `DELETE`、结果 `失败`、时间段筛选，结果随之收窄。
8. 用 zhangsan 访问 `/audit/page` 返回 403 —— 日志只对有 `system:audit:list` 的角色开放。

### 5. 子系统菜单与超级管理员

**子系统菜单（RBAC 统一给子系统配菜单）**

1. 用 admin 打开「系统管理 → 子系统管理」，能看到「订单中心」与「营销系统」，也可新增子系统（编码唯一，重复报 1025）。
2. 打开「系统管理 → 子系统菜单管理」，顶部下拉选一个子系统，即可给它维护**目录 / 菜单 / 按钮**三级菜单树（切换子系统会重新拉取该子系统的树）。三个层级的 `perms` 都会参与子系统侧的接口鉴权；按钮节点只作权限点、`visible=0` 不在导航里显示。
3. 在「角色管理」里给普通角色点「分配子系统菜单」：选子系统 → 勾选菜单 → 保存。保存是**按子系统局部替换**的，改 A 子系统的勾选不会动 B 子系统。
4. 删除有子菜单的菜单、或删除还有菜单的子系统，都会被拒绝（1011 / 1027），避免留下孤儿数据。

> 上面这些操作（菜单增删改、角色授权变更、用户增删改与角色变更）**每一次都会立即触发一次该子系统的 MQ 全量下发**，无需重启子系统。下发契约见「子系统接入」。

**超级管理员的权限不落库**

`admin` 角色在 `sys_role_menu` / `sys_role_subsystem_menu` 里**没有任何记录**，但依旧拥有全部系统权限与子系统权限：

- **权限判定**：登录快照里的权限集合直接是通配符 `*:*:*`，`PermissionService.hasPerm` 见到它一律放行 —— 因此新增菜单或接口后**不需要给超级管理员补授权**。
- **菜单渲染**：`/auth/info` 对超级管理员返回**全部启用菜单**，而不是按 `sys_role_menu` 过滤。
- **授权页**：admin 角色的「分配权限」「分配子系统菜单」按钮置灰并提示原因；即使直接调接口也会被拒（1028），授权树按"全选"回显 —— 那正是它实际的权限范围。

> 代价是超级管理员的权限**无法收窄**，它永远是全集。这是有意的设计；需要收窄的角色请用普通角色。

验证：

```sql
-- 应为 0：超级管理员没有落库的授权记录，但权限依然完整
SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON rm.role_id = r.id WHERE r.role_key = 'admin';
```

同时用 admin 登录后访问 `/subsystem/page`、`/audit/page`、`/client/page` 均应 200。

### 6. OAuth2 授权（第三方接入）

授权服务器在 rbac-oauth（7071），授权流程页面在 rbac-oauth-web（5174）。以示例客户端 `rbac-third-party`（密钥 `client123`）为例：

1. **客户端凭证模式** —— 不涉及用户，最快验证令牌端点：

   ```bash
   curl -u rbac-third-party:client123 -d grant_type=client_credentials http://localhost:7071/oauth/token
   ```

2. **密码模式** —— 用本系统 `sys_user` 表的账号密码校验：

   ```bash
   curl -u rbac-third-party:client123 -d grant_type=password -d username=admin -d password=admin123 http://localhost:7071/oauth/token
   ```

3. **授权码模式** —— 需要浏览器：

   1. 打开下面的地址（注意 `client_secret` 是本项目要求在授权地址上携带并校验的，标准 OAuth2 不需要）：

      ```
      http://localhost:7071/oauth/authorize?response_type=code&client_id=rbac-third-party&client_secret=client123&redirect_uri=http://localhost:7071/oauth/test-callback&scope=read&state=abc
      ```

   2. 未登录时会 302 到 rbac-oauth-web 的登录页（`http://localhost:5174/login`），用 admin / admin123 登录。
   3. 登录后自动跳回授权确认页（`/approve`），确认 clientId 与申请的 scope 后点「同意授权」。
   4. 浏览器带 `?code=...` 回到 `/oauth/test-callback`，这个联调端点会直接换令牌并解析出令牌里的用户信息。

   > 若第 4 步提示「回调地址不匹配」，说明 `clientdetails.redirectUrl` 里没有该地址：旧库执行 [upgrade-2026-10-16-oauth-server.sql](sql/upgrade-2026-10-16-oauth-server.sql)，或在「客户端管理」页把回调地址改到 7071。

4. **第三方开放接口** —— 用上一步拿到的 access_token 调用：

   ```bash
   curl -H "Authorization: Bearer <access_token>" http://localhost:7071/openapi/userinfo
   curl -H "Authorization: Bearer <access_token>" http://localhost:7071/openapi/users
   ```

   无令牌返回 401，scope 不足返回 403，错误体是 OAuth2 风格的 `error` / `error_description`（而非本系统那套「登录已过期」）。

### 7. 订单中心端到端（子系统接入）

前提：RocketMQ 已启动、`order-backend`(7073) 与 `order-frontend`(5176) 已启动、RBAC 至少下发过一次。

**登录与菜单**

1. 打开 `http://localhost:5176`：未登录，直接落到统一门户的登录页（子系统已经没有登录页了），用 admin / admin123 登录。
2. 登录成功后浏览器自动回到订单中心（门户→订单中心授权地址→静默发码），侧边栏只有「订单管理 → 订单列表 / 订单统计」两项（`order_menu` 表里下发的菜单树），仪表盘上「我的权限标识」显示 `*:*:*`。
3. 再打开 `http://localhost:5175`（营销系统）：**不需要再登录、也看不到登录页**。浏览器里已有授权服务的 SESSION，营销系统的 `/login` 去自己的授权端点，授权端点凭会话直接发码，静默把它送进系统。
4. 反过来把浏览器 SESSION 清掉（或换个无痕窗口）直接打开业务页，会被送到授权服务的登录页（`http://localhost:5174/login`）—— 这就是「子系统没有登录页、登录由授权服务承担」的表现。

**权限拦截（zhangsan / common）**

5. 退出后用 zhangsan / admin123 登录：仍能看到两个菜单，但「订单列表」页**没有「新增订单」按钮**，行内也没有「修改 / 删除」。
6. 直接调接口同样被挡：

   ```bash
   # 200：有 order:order:list
   curl -H "Authorization: Bearer <zhangsan_token>" "http://localhost:7073/order/page?pageNum=1&pageSize=10"
   # 200：有 order:stat:list
   curl -H "Authorization: Bearer <zhangsan_token>" http://localhost:7073/order/stat
   # 403：没有 order:order:add
   curl -X POST -H "Authorization: Bearer <zhangsan_token>" -H "Content-Type: application/json" \
        -d '{"orderNo":"X1","customerName":"越权","orderAmount":1}' http://localhost:7073/order
   # 401：不带令牌（或令牌无效）
   curl http://localhost:7073/order/stat
   ```

**MQ 下发的双向性（核心）**

7. 用 admin 在「角色管理 → 普通用户 → 分配子系统菜单」里，把子系统的「订单列表 → 订单新增」按钮也勾上并保存。
8. **不重启任何服务**，用 zhangsan 刷新订单中心页面：新增按钮出现，上一条 POST 直接返回 200。
9. 再取消勾选并保存，刷新页面：按钮消失，同样的 POST 变回 403。

   > 这就是「权限经 MQ 从 RBAC 同步到订单系统」：中枢每次改动都会发一条全量快照，订单侧收到后清空重建 4 张同步表，下一个请求即按新权限判定。

**业务与统计**

10. 用 admin 新增一条订单（订单编号、客户名称、金额、状态、下单时间），列表能查到，中文客户名与 `yyyy-MM-dd HH:mm:ss` 的时间都正常。
11. 用同一个订单编号再新增一次 → 提示「订单编号已存在」（2007）。
12. 删除后再用同一个编号新增 → 成功（删除是逻辑删除，服务端会把被占用编号改成 `原名_deleted_{id}` 腾出唯一键）。
13. 「订单统计」页显示订单总数、金额合计与按状态的分布，占比与列表数据一致。

### 8. 统一门户 SSO 端到端（单点登录 / 统一登出 / 会话管理）

前提：已执行 `sql/upgrade-2026-10-16-sso.sql`，`rbac-oauth`(7071)、`rbac-backend`(7070)、两个子系统后端与 `portal-frontend`(5177) 均已启动。

**单点登录**

1. 打开 `http://localhost:5177` → 点「统一登录」→ 在 rbac-oauth 的登录页用 admin / admin123 登录 → 自动回到门户工作台。
   （门户客户端已配 `autoApproveScopes`，不会再弹授权确认页。）
2. 点「一键登录所有子系统」：两张卡片的标签由「未登录」变成「已签发令牌」，全程没有二次登录、没有确认页。
3. 点任一卡片的「进入」，或直接打开 `http://localhost:5176` / `http://localhost:5175` —— 都**不需要再登录**。
4. 用 Redis 看一眼这次会话做到了什么：

   ```bash
   redis-cli get sso:session:admin
   # {"username":"admin","userId":1,"nickname":"超级管理员","loginTime":"...",
   #  "clients":["portal","order-center","marketing"]}
   redis-cli get sso:browser:admin   # 浏览器登录态的 token → oauth2:auth:{token}
   redis-cli get sso:uid:1           # → admin
   ```

   注意 `rbac-oauth` 与 `rbac-backend` **重启不会把用户踢下线**：JWT 的签名密钥来自 `jwt.jks`（不是启动时随机生成），SSO 会话又存在 Redis 里 —— 两者都不随进程重启消失。反过来说，想手工清场就 `redis-cli del sso:session:admin sso:browser:admin sso:uid:1`。

**统一登出（核心）**

5. 在门户点「统一登出」→ 停在授权服务的登录页（`http://localhost:5174/login`），`redis-cli get sso:session:admin` 返回空，`redis-cli get sso:browser:admin` 也空了。
6. **旧令牌下一请求即失效**：把登出前从子系统拿到的 `order_access_token` 拿来调订单中心接口 → 401。

   ```bash
   curl -H "Authorization: Bearer <登出前的旧令牌>" http://localhost:7073/order/stat
   # 401（授权服务的 check_token 发现 SSO 会话已不存在，判为无效令牌）
   ```

7. **刷新令牌也换不出新令牌**（否则登出会被绕过）：

   ```bash
   curl -u order-center:order123 -d grant_type=refresh_token \
        -d refresh_token=<登出前的旧刷新令牌> http://localhost:7071/oauth/token
   # 400 invalid_grant：登录会话已结束，请重新登录
   ```

8. **浏览器会话已销毁**：直接打开 `http://localhost:7071/oauth/authorize?response_type=code&client_id=order-center&client_secret=order123&redirect_uri=http://localhost:5176/oauth/callback&scope=read` → 会被要求重新登录（若 SESSION 还在，这里会直接发码）。
9. 在任一子系统（如订单中心）点「退出登录」，效果与第 5~8 步一致 —— 这就是「任一入口登出，所有子系统立即失效」。登出后浏览器落在**授权服务的登录页** `http://localhost:5174/login`（子系统没有自己的登录页，`redirect_uri` 指回本系统的 `/login` 再顺着统一入口走到那里），并且会带着待处理的授权请求 —— 在那儿重新输入账号密码能直接进得去。
10. **两种登录方式都要试一遍**：子系统的登录页有「账号密码登录」与「授权码登录」两条路，登出链路对两者都必须生效。密码模式那条尤其容易漏 —— 浏览器里没有授权服务的 SESSION，登出请求认不出人（前端因此是「带令牌的 fetch + 整页跳转」两步，见下文「统一门户 SSO」第 2 条）。
11. **标签页之间的联动**：把订单中心与营销系统各开一个标签页，在营销那边登出，然后**切回订单中心的标签页** —— 应该立刻被拦到登录页（靠切回时的惰性校验），而不是等你去点某个按钮。

**会话管理（管理端）**

12. 在 `http://localhost:5173` 用 admin 登录 → 「系统管理 → SSO 会话」：能看到刚才通过门户/子系统登录的在线用户，以及他登录过哪些客户端。
13. 换一个浏览器（或用 zhangsan）走一遍门户登录，回到 admin 的会话列表点该用户的「强制下线」→ 子系统里他立刻 401，浏览器会话被一并销毁（切回那个标签页也会立刻被拦到登录页）。
14. 用 zhangsan / common 登录管理后台 → 能打开「SSO 会话」页面、能查询，但**没有「强制下线」按钮**，直接调接口返回 403（`system:sso:kick` 没授给普通角色）。

**不影响既有流程（回归）**

15. `client_credentials` 令牌照常可用，且**不受统一登出影响**（没有用户，也就没有 SSO 会话）：多次调用 `curl -u rbac-third-party:client123 -d grant_type=client_credentials http://localhost:7071/oauth/token` 得到的令牌，调 `/openapi/userinfo` 仍返回 200。
16. RBAC 后台自身的登录态（`login:session:*`）不受影响；但用 admin 在后台「退出登录」，会**同时**清掉他的 SSO 会话 —— 「让这个人下线」在两边是同一件事。

## 统一门户 SSO

这一节讲清楚「一次登录、通行所有子系统」是怎么实现的，以及为什么必须这么实现。

### 要解决的问题

RBAC 授权服务器签发的 access token 是**自包含 JWT**（RSA 签名，不落库）。好处是任何服务拿公钥就能验签、不用回源；代价是**签出去就收不回来**——服务端手里没有它的任何记录，因此「登出」在纯 JWT 方案里根本无处落地。原来的子系统登出只是清掉 localStorage，服务端令牌照旧有效直到过期。

### 解法：给令牌套一层服务端会话

在令牌之外加一层 Redis **SSO 会话**，把令牌绑到会话上：

```
sso:session:{username}   SSO 会话 JSON，存在 = 令牌可用                TTL 7 天
sso:browser:{username}   该用户浏览器登录态的 token                     TTL 7 天
sso:uid:{userId}         userId → username 反查索引                    TTL 7 天
oauth2:auth:{token}      授权服务的登录态（精简认证信息，见下）           TTL 30 分钟
```

**授权服务自己的登录态也不用 Spring Session**：用户在这台浏览器上认证过这件事，直接存成一条 JSON ——
`oauth2:auth:{token}` = `{"userId":1,"username":"admin","nickname":"超级管理员","loginTime":"..."}`，
浏览器侧只有一个 HttpOnly Cookie `RBAC_OAUTH2_AUTH`。授权端点每次请求由 `OauthAuthCookieFilter`
凭 Cookie 查这条记录还原身份。这样 Redis 里是能看懂的认证信息，而不是序列化后的整团 `SecurityContext`；
踢人时一条 `GET oauth2:auth:{token}` 就知道是谁。HTTP 会话只剩 Spring MVC 暂存的「待授权的请求」
（授权确认页要用），不是认证信息。

| 时机 | 做什么 | 代码位置 |
|---|---|---|
| 令牌签发（密码/授权码/隐式） | 建立或续期会话，把客户端记进 `clients` | `SsoTokenServices#createAccessToken` |
| 刷新令牌 | 会话不存在就拒绝（`invalid_grant`）；存在则续期 TTL | `SsoTokenServices#refreshAccessToken` |
| `/oauth/check_token` | 会话不存在就当令牌无效 → 子系统立刻 401 | `SsoTokenServices#readAccessToken` / `loadAuthentication` |
| 授权前的表单登录 | 写 `oauth2:auth:{token}` + 下发 Cookie | `SsoLoginSuccessHandler` |
| 每个授权请求 | 凭 Cookie 还原登录态 | `OauthAuthCookieFilter` |
| 统一登出 / 后台踢人 | 删 SSO 会话 + 删登录态 | `SsoLogoutController` / `SsoSessionStore` |

「删会话即下线」这套做法沿用了 rbac-backend 管理端登录态（`login:session:*`）的既有模式，只是把作用域从「一个系统」放大到「所有子系统」。

**为什么会话键用 username 而不是 userId**：令牌里唯一权威的身份就是 `user_name` 声明 —— 服务端每次校验只拿得到令牌本身，而 `user_name` 在访问令牌与刷新令牌里**始终存在**。用它作键，校验路径就是一次 Redis GET、不查库；刷新路径也能直接从刷新令牌的认证对象取到同一个值，不必反查 `sys_user` 补 userId。子系统本来就按 `user_name` 认人，键与之一致也避免了「会话按 userId、业务按 username」两套身份对不上。

### 几个容易踩空的地方

1. **`check_token` 的校验点放在 `readAccessToken`，而不是自己写一个 `/oauth/check_token`。** 自己写 `@RestController` 映射同一个路径会和库自带的 `CheckTokenEndpoint` 撞车（Ambiguous mapping，启动即失败）。而库的端点第一步就是调 `readAccessToken`，返回 null 即抛 `InvalidTokenException` —— 覆写它既不用碰端点，又天然覆盖了子系统那条链路。

2. **登出要做两件事，而前端必须用两种姿势各发一次请求。**
   - *要清的是两个独立的东西*：① SSO 会话（删它 = 所有子系统的令牌失效）；② 授权服务的登录态 `oauth2:auth:{token}`（授权服务靠它记住「这个人认证过了」）。只删 ① 的话，浏览器里的 Cookie 还在，拿它静默重走一遍授权码就能换出新令牌，而签发新令牌会**重建** SSO 会话 —— 统一登出当场被绕过。
   - *难点在于两种请求能带的东西不一样*：

     | 怎么发请求 | 能带 `Authorization` 头 | 能带 Cookie |
     |---|---|---|
     | `fetch`（XHR） | 能 | 能 |
     | 整页跳转 | **不能** | 能 |

   - 于是前端两步都做（见 `portal-frontend` / `order-frontend` / `marketing-frontend` 的 `utils/sso.js`）：
     1. `fetch POST /oauth/sso-logout`，带 `Authorization: Bearer <本系统令牌>`（授权服务一侧已配好带凭证的 CORS）—— 靠它**认人并删掉 SSO 会话**；
     2. 整页跳转 `GET /oauth/sso-logout?redirect_uri=...` —— 靠它凭 Cookie 认出登录态、删掉 `oauth2:auth:{token}`、写一个过期的 Cookie 把浏览器那份丢掉，并顺手把用户带到登录页。

   - **为什么不能只做第 2 步（这是实际踩到的坑）**：密码模式登录的用户，浏览器里根本没有授权服务的登录态 —— 密码是子系统后端带客户端凭证去换的令牌，浏览器全程没参与。跳转请求既没 Authorization 头也没 Cookie，授权服务认不出「是谁在登出」，SSO 会话原封不动，于是**从营销系统登出后，订单中心仍然进得去**。
   - **为什么不能只做第 1 步**：被踢下线的标签页手里已经**没有有效令牌**了，它的 fetch 带不出能认人的东西；只有整页跳转那份 Cookie 还能让授权服务认出「是谁」。少了这一步，Redis 里的 `oauth2:auth:{token}` 就清不掉，用户回到授权页仍是登录状态。
   - 后台踢人两条都不适用（管理员手上既没有对方的令牌也没有 Cookie），所以走第三条路：登录成功时把 `sso:browser:{username} → token` 记进 Redis，踢人时据此删掉 `oauth2:auth:{token}`。

3. **刷新令牌要拒绝已登出的人。** `refreshAccessToken` 里必须用 `touch`（只在键已存在时延长 TTL）而不是 `createOrRenew`：后者会把登出时删掉的会话重新建出来，等于给登出留了个后门。

4. **子系统的 `check_token` 缓存必须去掉。** 原来每个请求缓存 5 分钟（「令牌在有效期内身份不会变」），那会让「用户已在别处登出」最长滞后 5 分钟生效。现在每次请求实时校验，代价是授权服务侧一次本地验签 + 一次 Redis GET，不查库。

5. **`client_credentials` 令牌不受影响。** 它没有用户、没有 SSO 会话，统一登出管不着它 —— 这是期望行为：机器身份不该因为某个人的登录状态变化而失效。

6. **跨源的子系统不会「被告知」登出，靠惰性校验补上体感。** 服务端是**立即**失效的，但各子系统跑在不同的源（5175 / 5176 / 5177）上，彼此之间没有任何通道（localStorage / BroadcastChannel 都按源隔离）。默认表现是「下次请求才被踢回登录页」—— 用户切回订单中心、只是看着不点任何东西的话，看到的还是登录状态的页面，很容易以为登出没生效。所以三个前端都在布局里挂了 `utils/sessionWatch.js`：**切回本标签页（`visibilitychange` 变可见，或用 `focus` 兜住两窗口并排的情况）时，悄悄请求一次登录态接口**。请求走同一个 axios 实例，因此复用了既有的处理链 —— access token 只是过期时静默续期（用户无感），会话已结束则清凭证跳登录页。
   - 为什么不用轮询：定时轮询会在用户根本没看这个页面时也持续发请求，而「切回标签页」这个时机已经覆盖了绝大多数会产生「怎么还登录着」疑问的场景，代价却是零。
   - 同一轮 `visibilitychange` + `focus` 会用 1 秒节流折叠成一次请求。

### 一键登录所有子系统是怎么做到的

门户登录时浏览器已经持有授权服务的 SESSION，因此其余子系统可以**静默**走完授权码流程（两个子系统客户端的 `autoApproveScopes` 都放行了 `read`，连确认页也跳过）：

```
门户「一键登录」→ 对每个未登录的子系统：
    隐藏 iframe 打开 /portal/sso-login/{code}
      → 后端拼好该子系统的授权地址并 302（前端拿不到 client_secret）
      → 授权服务凭 SESSION 直接发码（不弹登录页、不弹确认页）
      → 跳到子系统前端 /oauth/callback?code=...
      → 子系统自己把 code 交给它的后端换令牌，存进它自己源的 localStorage
    门户轮询 /portal/info，直到 SSO 会话的 clients 里出现该子系统 → 这一步确实完成了，进入下一个
```

进度判断**不靠猜 iframe 加载完没有**，而是读服务端的事实（`clients` 由授权服务在签发令牌时写入）。某个子系统超时未完成时，门户会提示手动点「进入」，不会假装成功。

### 子系统的登录入口：直接去授权服务的登录页

子系统**没有登录页**：`/login` 保留着，但它不是表单，而是一个跳转页（路由守卫、401 拦截器、登出回跳都指向 `/login`，保留这个路径它们就都不用改）。

```
未登录访问 5176
  → /login 导航到后端 GET /auth/sso-login（后端拼授权地址并 302，前端不接触密钥）
  → 授权端点发现没登录 → 302 到 http://localhost:5174/login
  → 输账号密码 → 302 回那次授权 → 凭会话发码 → /oauth/callback 存令牌 → 落地
```

**落点统一**：主动打开子系统、在子系统点「退出登录」、在门户点「统一登出」、被踢下线，最终都停在**授权服务的登录页** `http://localhost:5174/login`。门户不参与子系统的登录链路 —— 它是「一键登录所有子系统」的入口，不是登录页。

落点必须**带着待处理的授权请求**：浏览器是经由 `/oauth/authorize` 被 302 到 5174 的，会话里缓存着「哪个客户端要授权、码发到哪」。如果直接把浏览器丢到 5174/login（会话里没有待授权的请求），用户输完账号密码会被弹回登录页 —— 5174 的 `/` 就是重定向到 `/login`，等于登不进去。所以「子系统登出」的回跳地址是**本系统自己的 `/login`**，由它再走一遍授权流程。

这套设计有个附带好处：**已经登录过的人不会看到登录页**。授权端点先查会话，有会话就静默发码，直接落进子系统 —— 「登录一次，进第二个子系统免登录」仍然成立。

三个设计取舍：

- **授权地址只由后端持有**：各子系统用 `GET /auth/sso-login`、门户用 `GET /portal/sso-login`（以及一键登录的 `GET /portal/sso-login/{code}`），都是「后端拼好地址 → 302」；前端只做一次导航，**接口不返回任何带 `client_secret` 的地址**。目标不是由外部输入决定的（门户那条按 `code` 在已登记子系统里查），所以也不是开放重定向。
- **没有登录页意味着出错时可能「无落脚点」**，所以取授权地址失败时会停在跳转页给出原因与「重试」按钮，而不是白屏或跳到一个空地址。
- **登出只有点登出的那个标签页会立刻跳走**；其他标签页要等下一次请求（或切回来时的惰性校验）才发现自己已被踢，然后同样落到 5174/login。

### 权限点

| 权限点 | 用途 |
|---|---|
| `system:sso:list` | 进入「SSO 会话」页面 |
| `system:sso:query` | 查询在线会话 |
| `system:sso:kick` | 强制下线（普通角色默认**不**授予） |

### 相关接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `rbac-oauth:7071/oauth/sso-logout?redirect_uri=` | 统一登出（整页跳转），清 SSO 会话 + 浏览器会话后 302 回 `redirect_uri`（只接受已登记客户端的来源，否则回授权服务登录页） |
| POST | `rbac-oauth:7071/oauth/sso-logout` | 同上，XHR 版，返回 JSON |
| GET | `rbac-backend:7070/portal/sso-login` | 门户登录：拼好门户的授权地址并 302（无需令牌，不返回地址） |
| GET | `rbac-backend:7070/portal/sso-login/{code}` | 一键登录的某一跳：拼好该子系统的授权地址并 302（无需令牌，不返回地址） |
| POST | `rbac-backend:7070/portal/token` | 授权码换门户令牌（服务端代换，因为要带 client_secret） |
| GET | `rbac-backend:7070/portal/subsystems` | 子系统清单（编码 / 名称 / 入口地址 / 可用状态，**不含授权地址**，需门户令牌） |
| GET | `rbac-backend:7070/portal/info` | 当前身份 + 已登录的客户端列表（需门户令牌） |
| GET | `rbac-backend:7070/sso/session` | 在线会话列表（需 `system:sso:list`） |
| DELETE | `rbac-backend:7070/sso/session/{userId}` | 强制下线（需 `system:sso:kick`） |

## 子系统接入

「子系统」= 一个独立库 + 独立后端 + 独立前端的业务系统，**自己不做登录、不做权限管理**，把这两件事整体交给 RBAC。订单中心与营销系统都是按这套模式接入的，接入步骤可直接照抄。

### 认证：统一走 rbac-oauth，登录页就是授权服务的登录页

子系统不存密码、**没有自己的登录页**：未登录访问时直接去授权服务的登录页（`http://localhost:5174/login`），登录完再回到本子系统。统一门户是另一个独立入口（见「统一门户 SSO」），**不在子系统的登录链路上**。

```
未登录访问 5176
  → 子系统的 /login（不是表单，是个跳转页）
  → 导航到后端 GET /auth/sso-login：由后端拼好授权地址并 302（地址带 client_secret，只能服务端持有）
  → 授权端点发现没登录 → 302 到授权服务的登录页 http://localhost:5174/login
  → 输入账号密码 → 302 回刚才那次授权 → 凭会话直接发码（autoApproveScopes 已放行 read）
  → 订单中心 /oauth/callback 用 code 换令牌并存 localStorage → 落地在订单中心
```

**前端全程不接触 `client_id` / `client_secret`**：授权地址由后端拼接、由后端发 302，前端只做一次「导航」。以前那个「把授权地址作为 JSON 返回给前端」的接口（`/auth/authorize-url`）已经下线 —— 那会把密钥交给前端代码、浏览器网络面板和前端日志。门户那边同理（`/portal/sso-login`、`/portal/sso-login/{code}`）。

已经登录过（浏览器里还有授权服务的会话）时，第二步之后的登录页**根本不会出现**：授权端点直接发码，用户就静默落进子系统 —— 「登录一次，进第二个子系统免登录」靠的就是这个会话。

| 模式 | 谁在用 | 链路 |
|---|---|---|
| 授权码模式 | **子系统前端唯一的方式** | 见上面的流程图 |
| 密码模式 | 第三方接入 / curl 联调（子系统前端不再使用） | 直接调子系统 `/auth/login` → 子系统带客户端凭证调 rbac-oauth `/oauth/token`（`grant_type=password`）→ 返回令牌 |

> 密码模式为什么从子系统前端撤掉：那个流程是「子系统后端带客户端凭证去换令牌」，**浏览器全程没参与**，因此浏览器里不会留下授权服务的 SESSION。这让统一登出多出一整类特例（纯跳转式登出认不出密码模式登录的用户，详见下文「统一门户 SSO」第 2 条）。所有子系统登录都走授权码后，浏览器一定有 SESSION，登出链路只剩一种形态。
> 后端的密码模式与 `/auth/login` 仍然保留，供第三方接入与联调使用。

子系统前端把令牌存在 `localStorage`（键名带子系统前缀，互不覆盖）。之后每个请求带 `Authorization: Bearer`，子系统后端取令牌调 `/oauth/check_token` 换回 `user_name`，**仅凭用户名**去本地的同步表找用户与权限 —— 令牌本身不携带权限。

> 换回用户名之后还有一道校验：这个用户名必须存在于本子系统的同步表里且状态为在职，否则直接拒掉（2004）。令牌有效只说明「他是 RBAC 的合法用户」，不等于「他被授权使用本子系统」。

### 授权：MQ 下发（`RBAC_SUBSYSTEM_SYNC`）

**子系统没有任何配置权限的界面**，权限数据只来自 MQ：

```
RBAC (rbac-backend)                               子系统 (order-backend)
  sys_subsystem_menu  ┐                              ┌  order_menu
  sys_role_subsystem_menu ┤── 全量快照 ──► topic ──►   ├  order_role_menu
  sys_user / sys_user_role ┘   (JSON)     RBAC_SUBSYSTEM_SYNC:order-center
                                                     ├  order_user
                                                     └  order_user_role
```

- **topic** `RBAC_SUBSYSTEM_SYNC`，**tag = 子系统编码**（`order-center` / `marketing`）。子系统用 `selectorExpression` 只订阅自己那一条，消费组各自独立（`order-sync-consumer` / `marketing-sync-consumer`），互不抢消息。
- **消息体**：`subsystemCode` + `menus` + `roleMenus` + `users` + `userRoles` 四份**全量**列表（不是增量）。
- **触发时机**：用户增删改与角色变更、角色-子系统菜单授权变更、子系统菜单增删改，以及 rbac-backend **应用启动时全量下发一次**。发送在事务提交之后，避免「消息发了但事务回滚了」。
- **消费语义**：子系统收到后在一个事务里**清空重建** 4 张同步表（`order_user` / `order_menu` / `order_role_menu` / `order_user_role`）。全量快照 + 清空重建比逐条 diff 简单可靠，也不会因漏处理某条删除而留下过期授权。
- **超管不落库**：`admin` 的授权在中枢与子系统都不落表。子系统靠同步下来的 `role_keys` 里是否含 `admin` 来识别，识别到就直接给通配符 `*:*:*` 与全部启用菜单。

### 判权：每个请求实时查同步表

```
Bearer 令牌 → /oauth/check_token → user_name → order_user
           → order_role_menu ⋈ order_user_role ⋈ order_menu.perms
           → @PreAuthorize("@ss.hasPerm('order:order:add')")
```

同步表就是权限的唯一事实来源，所以中枢改一次授权，子系统**下一个请求**就生效 —— 不需要重新登录，也不需要重启。

### 接入一个新子系统要改哪些地方

1. 中枢库里建子系统与菜单树：`sys_subsystem` 一行 + `sys_subsystem_menu` 若干行（目录/菜单/按钮，带 `perms`）+ `sys_role_subsystem_menu` 给角色授权。
2. 中枢库里登记 OAuth2 客户端：`clientdetails` 一行（appId、AES 密文密钥、grantTypes、**redirectUrl 必须与前端回调页完全一致**）。
3. 新库：`*-schema.sql` 建 4 张同步表（结构照抄 `order-schema.sql`，表名前缀换掉）+ 自己的业务表。
4. 新后端：照抄 `order-backend` —— 改包名、库名、端口、`oauth2.client-id/client-secret/redirect-uri`、`SUBSYSTEM_CODE`、消费组名，以及权限点前缀。
5. 新前端：照抄 `order-frontend` —— 改端口、`/api` 代理目标、`localStorage` 键名、`VITE_OAUTH_SERVER_URL`、品牌文案；业务页的目录路径必须与 `sys_subsystem_menu.component` 一一对应。登录相关四块照抄即可，不用自己实现：
   - `views/login/index.vue` —— 不是表单，是「跳转门户」的页面（含失败兜底）；
   - `views/oauth/callback.vue` —— 用 code 换令牌；
   - `utils/sso.js` / `utils/sessionWatch.js` —— 统一登出与「切回标签页校验登录态」。
6. 门户那边**不用改**：`/portal/subsystems` 直接读 `sys_subsystem` ⋈ `clientdetails`，新子系统登记好就会出现在门户的卡片里，也能被一键登录。

## 拆分后的共享约定

中枢两个后端是独立的 Spring Boot 进程，但**共用同一个 MySQL 库与同一套 Redis**；子系统则各自独立一个库、不连 Redis，只通过 **MQ 与授权服务器**与中枢发生关系。跨进程的耦合点只有下面几处，改动时需两边同步：

- **客户端密钥**：`clientdetails.appSecret` 在库里是 AES 密文，中枢两服务都用 `oauth2.client-secret-key` 加解密 —— rbac-backend 写入时加密、回显时解密（页面要展示明文给第三方），rbac-oauth 解密以校验客户端身份。**两处配置必须一致**，否则客户端会全部认证失败。子系统不需要这个配置项（它们只持有自己的明文密钥）。
- **Redis 键**：`login:session:{userId}`（当前会话）、`login:refresh:{sessionId}`、`login:perm:{sessionId}`（权限快照）由 rbac-backend 维护。rbac-oauth 只读 `login:session:*`，并在第三方经 `/openapi` 停用/离职用户时删除这三个键（立即下线）。键名变更需两边同步。子系统不使用 Redis —— 它的「会话」就是授权服务器签发的令牌。
- **SSO 会话键（统一门户）**：`sso:session:{username}`（会话本体，存在=令牌可用）、`sso:browser:{username}`（该用户浏览器登录态的 token）、`sso:uid:{userId}`（userId → username 反查）由 **rbac-oauth** 写入，**rbac-backend** 读列表并按 userId 删除（并在删 `login:session` 时连带清除，见 `TokenStore#removeSession`）。授权服务的登录态 `oauth2:auth:{token}` 由 rbac-oauth 的 `OauthAuthStore` 写入，踢人时 rbac-backend 按 `sso:browser:{username}` 取出 token 并删掉它。键名变更需两边同步，细节见「统一门户 SSO」一节。
- **库表**：`clientdetails` 由 rbac-backend 读写（客户端管理），rbac-oauth 只读（加载客户端做认证）；`sys_user` 两边都读 —— rbac-oauth 用它做密码模式的身份校验与开放接口的用户访问。子系统**不读中枢的任何表**。
- **MQ 下发契约**：topic `RBAC_SUBSYSTEM_SYNC`、tag = 子系统编码、消息体字段名（`subsystemCode` / `menus` / `roleMenus` / `users` / `userRoles`）。中枢的生产者（`SubsystemSyncService`）与子系统的消费者（`SubsystemSyncConsumer` + `*-SyncService`）是**两份各自独立的代码**，字段名或语义变更必须两边同步改 —— 这里没有共享库或共享 DTO 来兜住。见「子系统接入」。
- **授权服务里没有 RBAC**：rbac-oauth 不解析后台的登录令牌，也没有权限快照与 `@PreAuthorize`。要改客户端配置只能走 rbac-backend 的「客户端管理」页（`system:client:*` 权限 + 操作日志）。
- **操作日志**：`sys_operation_log` 由 rbac-backend 独占写入（含 `@OperLog` 切面）。客户端配置的增删改照旧留痕；**唯一不再留痕的是**第三方经 `/openapi/users/{id}/status` 停用/离职用户（原本会记一条 `DISABLED`/`RESIGNED`，**强制下线本身仍然生效**）。不把日志表与切面复制到授权服务，是为了避免两处实现漂移；若要审计第三方的动作，应在授权服务侧补一个精简写入。子系统里同样没有操作日志。

## 说明与注意事项

- **两个有效期**：`jwt.access-token-expire`（默认 15 分钟）是 Access Token 的轮换周期，过期后由前端调 `/auth/refresh` 续期；`jwt.refresh-token-expire`（默认 7 天）既是 Refresh Token 的有效期，也是 Redis 会话的有效期 —— 即登录态的真正上限。JWT 的 `exp` 只决定「该续期了」，真正的准入判断是 Redis 会话，所以删除会话（离职/停用/踢下线）能立即生效，而不必等 Token 过期。
- **登录快照与失效**：用户信息、权限、角色在登录时固化为会话快照（`login:perm:{sessionId}`，TTL 与 Refresh Token 一致），之后每个请求直接由快照重建认证主体，不再访问数据库。为保证「改了立刻生效」，以下操作会**精准失效**受影响用户的快照，使其下一次请求重建：角色授权变更（失效该角色下全部用户）、用户资料修改、用户角色变更，以及会话删除（登出/停用/离职/踢下线/重置密码，随会话一并清理）。只有菜单树仍实时查库，因此改菜单名称立即反映。
  **唯一的例外**：用 SQL 直接改 `sys_menu` / `sys_role_menu`（如执行增量脚本）绕过了失效逻辑，已登录用户读到的仍是旧快照，需重新登录，或按 [upgrade-2026-10-13-audit.sql](sql/upgrade-2026-10-13-audit.sql) 顶部的说明清一次 `login:perm:*`。
- **密码散列不进快照**：`SysUser.password` 标注了 `@JsonIgnore`，快照里没有它。需要校验密码的场景（自助改密）因此单独查一次库 —— 顺带保证校验的是「库里此刻的散列」，而不是会话建立时的旧值。
- **停用/离线的即时性靠删会话，不靠快照**：用户被停用或标记离职时删掉的是 Redis 会话，认证过滤器比对会话时就把请求挡在门外，所以即便快照里的 `status` 是登录时的旧值，也依然立刻下线。
- **逻辑删除与唯一键**：`sys_user` / `sys_role` / `sys_post` 的删除是**逻辑删除**（`del_flag=1`），行仍物理留在表里，唯一键也仍被占用。所以删除时会先把业务唯一字段改名为 `原名_deleted_{id}`（见 [UniqueKeyUtils](rbac-backend/src/main/java/com/example/rbac/util/UniqueKeyUtils.java)），否则被删掉的那个用户名 / 角色标识 / 岗位编码将永远无法再创建 —— 应用层的重名校验会放行（它排除了已删行），随后 INSERT 撞上数据库唯一键，用户只看到「系统异常」。若你的库里有更早版本的已删行，执行 [upgrade-2026-10-13-release-unique-key.sql](sql/upgrade-2026-10-13-release-unique-key.sql) 补上改名。
- **两条改密路径**：管理员在「用户管理」里重置他人密码（`PUT /user/{id}/resetPassword`）会**强制该用户下线**，适用于账号被盗或密码遗忘；用户自己在右上角「修改密码」（`PUT /auth/password`）需校验原密码，改完**当前会话继续有效**，不会被自己踢下线。两条路径都会写进操作日志（`RESET_PWD` / `CHANGE_PWD`）。
- **操作日志**：`sys_operation_log` 只追加不修改、不提供删除接口，两类写入的时机不同：登录/登出/下线/改密由业务代码**在事务内**直接写，写失败会让该次操作一并失败；增删改由切面在业务事务**提交之后**写，失败只记 error 日志 —— 业务已经生效，这时再抛异常回给前端一个失败是误导。
- **刻意不记请求参数与请求体**：日志只记请求方法 + 路径（且不含查询串）。`PUT /user/{id}/resetPassword?password=xxx` 的参数、以及用户表单里的密码都在参数里，通用采集极易把密码明文落库。代价是「新增/修改用户」这类接口看不出具体目标（删除、授权等路径里带 id 的接口仍能看出）。
- **ip 字段**：优先取 `X-Forwarded-For` 首段（经反向代理时），否则取 `remoteAddr`；该值客户端可控，已截断到字段长度以内。
- **超级管理员**：权限**不落库**，由代码隐式授予（[SuperAdmin](rbac-backend/src/main/java/com/example/rbac/security/SuperAdmin.java)）：权限集合是通配符 `*:*:*`，菜单树直接返回全部启用菜单，因此新增菜单后无需给 `admin` 补授权，`sys_role_menu` / `sys_role_subsystem_menu` 里也没有它的记录（授权接口对它返回 1028）。代价是它无法被收窄 —— 永远是全集。内置 `admin` 角色与 `admin` 用户不允许删除。
- **单会话模型**：一个用户同时只有一个有效会话，后登录会顶掉先前的会话。
- **子系统的判权与中枢不同，别照搬心智模型**：中枢把「用户+权限+角色」在登录时固化成 Redis 快照，之后每请求读快照、不查库；子系统**没有快照**，每个请求都从同步表实时联查权限点。因此中枢改授权后靠「精准失效快照」生效，子系统则天然即时生效、无需任何失效动作。反过来，子系统多了一次「用户表 + 权限点联查」的开销，换来的是任何时刻都绝不会用到过期权限。
- **子系统不受中枢的单会话约束**：单会话是中枢 Redis 会话带来的性质；子系统只验令牌，同一账号在多个浏览器/多台机器可并存（令牌各自独立过期）。
- **MQ 下发是幂等的**：消息体是全量快照、消费语义是清空重建，所以重复投递、乱序投递都不会把数据搞乱 —— 最后一条消息就是最终状态。代价是每次下发都要重写整张同步表，菜单与用户规模大时应改为增量（当前规模下不值得）。
- **子系统里的唯一键同样要「腾」**：`order_info.order_no`（以及营销侧的 `activity_code` / `task_code`）都是**逻辑删除**，删除时会把被占用的编号改名为 `原名_deleted_{id}`，否则那个编号永远无法再使用。机制与中枢一致，见 [UniqueKeyUtils](order-backend/src/main/java/com/example/order/util/UniqueKeyUtils.java)。
- **授权码模式的 `state` 目前只在发起时写入 `sessionStorage`，回调页未做校验**：订单中心与营销系统两处实现相同（见各自 `views/login/index.vue` 与 `views/oauth/callback.vue`）。由于本项目在授权地址上额外强制校验 `client_secret`，实际风险有限，但标准做法应在回调页比对 `state` 再换令牌 —— 要收紧的话两个前端需一起改。
