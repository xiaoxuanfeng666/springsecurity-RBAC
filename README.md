# RBAC 权限管理后台

基于 **Spring Boot + Spring Security + MySQL + Redis + RocketMQ + Vue 3** 的前后端分离 RBAC 权限管理系统，内置独立的 **OAuth2 授权服务器**供第三方系统接入，并附三个**已接入的独立子系统**（订单中心、营销系统、审计日志系统）演示「认证与授权统一交给中枢、业务系统自己完全不碰用户与权限」。

## 核心能力

| 需求 | 实现方式 |
|---|---|
| **无感知续期** | Access Token 短有效期（由 `clientdetails.access_token_validity` 决定，默认 30 分钟）。RBAC 管理后台与订单中心 / 营销系统 / 审计日志系统统一采用 **BFF**：浏览器只持一个 HttpOnly 会话 Cookie，令牌保存在各自服务端 Redis，过期后由后端静默续期，前端不接触任何令牌。用户全程无感知，不需要重新登录。 |
| **离职立刻失效凭证** | 用户状态三值：**1 在职 / 0 停用 / 2 离职**。登录态由 rbac-oauth 统一管理（`sso:session:{username}`），rbac-backend 与各子系统每个请求都**本地验签 JWT**（公钥来自 `/oauth/token_key`），不再每请求回源 `/oauth/check_token`。标记停用或离职时经 MQ（`RBAC_OAUTH_SESSION`）通知 rbac-oauth 删除该用户的 SSO 会话（`sso:session:{username}` + `oauth2:auth:{token}`），后台与订单中心、营销系统等所有子系统手里的存量令牌在下一次请求即被拒绝，刷新令牌也换不出新的，无需等待自然过期。 |
| **登录快照（用户 + 权限 + 角色）** | 用户信息、权限、角色在首次建立登录态时查库一次，整体序列化写入 Redis（`login:perm:user:{username}`）。之后每个请求都由快照重建认证主体，**不再访问数据库** —— 省掉「用户-角色-菜单」的多表联查与逐请求的用户行查询。 |
| **动态修改角色权限** | 管理员在角色授权页调整「角色-菜单」映射后，服务端立即**失效该角色下所有用户的权限快照**，用户下一次请求重建快照并拿到新权限，无需重新登录。 |
| **子系统菜单统一配置** | RBAC 作为中枢登记各子系统（每个子系统有**唯一编码**，如 `order-center` / `marketing` / `audit`），并在「菜单管理」页顶部按「所属系统」切换：选 RBAC 本系统维护 `sys_menu`，选某个子系统直接维护该子系统的 `sys_subsystem_menu`（目录 / 菜单 / 按钮三级，均带 `perms`）。顶部「新增子系统」可直接登记**子系统名称 + 唯一编码**，保存后自动切换到该子系统继续维护菜单。角色的子系统授权仍按子系统单独保存，并**经 RocketMQ 实时下发给对应子系统**。 |
| **超级管理员免配置** | `admin` 的权限**不落库**：代码里直接给通配符 `*:*:*` 与全部启用菜单。新增菜单或接口后不需要再手动给它补授权，`sys_role_menu` 里也不会有它的记录。 |
| **操作日志与审计日志** | **账号/会话类动作**（登录、登出、强制下线、停用/启用/离职、重置密码、自助改密）留在 RBAC 库的 `sys_operation_log`；**各系统的接口增删改**（新增、修改、删除、授权）统一上报到独立的**审计日志系统**（独立库 `audit_log`），记录来源系统、模块、动作、操作人、请求方法路径、**已脱敏的请求参数**、成败、耗时、来源 IP。登录/登出**不进入**审计日志。各系统经 RocketMQ topic `RBAC_AUDIT_LOG` 上报，审计系统只做接收与查询。 |
| **统一登出** | RBAC 管理后台与各子系统共享授权服务上的登录态：任一系统登出即删掉该用户的 SSO 会话，**所有系统**手里的旧令牌下一个请求就失效，刷新令牌也换不出新的（自包含 JWT 本身删不掉，靠这层服务端会话把生死收回来）。已经登录过的人再进另一个系统**不弹登录页**（凭会话直接发码）。见「SSO 会话与统一登出」一节。 |
| **多 IdP 登录（企业微信 / Mock）** | 登录页在账号密码之外动态展示已启用的外部身份提供方（企业微信、Mock）。统一走 `/oauth/idp/{provider}/**`：认证成功后查绑定、按策略自助绑定或 JIT 自动建号，再建立与密码登录**完全相同**的 SSO 登录态，回到最初的 `/oauth/authorize` 继续发授权码。第三方授权码流程不变。rbac-backend 另提供通讯录/部门同步（Mock 可测）。 |

## 服务划分

授权相关代码已从 RBAC 后台拆出，另有**三个已接入的独立子系统**（订单中心、营销系统、审计日志系统）演示「RBAC 作为统一认证与授权中枢，业务系统自己完全不碰登录与权限管理」：

| 服务 | 端口 | 类型 | 职责 |
|---|---|---|---|
| **rbac-backend** | 7070 | 中枢 | RBAC 业务：登录/续期、用户、角色、菜单、部门、岗位、客户端管理、子系统与子系统菜单、操作日志、**用户企业微信身份绑定** |
| **rbac-oauth** | 7071 | 中枢 | OAuth2 授权服务器：五种授权模式的端点、**统一登出 `/oauth/sso-logout`**、**企业微信扫码登录 `/oauth/wecom/**`**、第三方开放接口 `/openapi/**` |
| **rbac-frontend** | 5173 | 中枢 | RBAC 管理后台界面：登录跳统一授权（无本地登录表单）、动态路由、按钮级权限 |
| **rbac-oauth-web** | 5174 | 中枢 | OAuth2 授权流程页面：授权登录页（账号密码 + **企业微信扫码入口**）、授权确认页。它要与授权端点同源（靠 `/oauth → 7071` 代理），因为登录页是用原生表单 POST 到 `/oauth/login` |
| **order-backend** | 7073 | 子系统 | 订单中心后端：订单增删改查 + 统计汇总。独立库 `order_center`，无登录、无用户表 |
| **order-frontend** | 5176 | 子系统 | 订单中心界面：未登录时直接去授权服务的登录页（无自己的登录页）、动态路由、按钮级权限 |
| **marketing-backend** | 7072 | 子系统 | 营销系统后端：抽奖活动、奖品、积分任务、流水与记录。独立库 `marketing` |
| **marketing-frontend** | 5175 | 子系统 | 营销系统界面（结构同 order-frontend） |
| **audit-backend** | 7074 | 子系统 | 审计日志系统后端：消费各系统上报的增删改操作并落库，提供分页/详情查询。独立库 `audit_log` |
| **audit-frontend** | 5178 | 子系统 | 审计日志系统界面：SSO 登录、按来源系统/模块/动作/操作人/结果/时间筛选、请求参数抽屉 |

两个中枢后端**共用同一个 MySQL 库与同一套 Redis**，配置上有几项必须一致，见文末「拆分后的共享约定」。

子系统之间、以及子系统与中枢之间**没有共用表、没有外键、没有跨库查询**：每个子系统一个独立库，登录与权限全部来自 RBAC，业务数据全部属于自己。**审计日志系统是汇总方**：它不产生业务操作，只消费其他系统（含 RBAC 自己）经 MQ 上报的操作记录。

## 技术栈

- **后端**：Spring Boot 2.7.18 / JDK 8 / Spring Security 5.7 / MyBatis-Plus 3.5 / Maven
  - rbac-oauth 另用 spring-security-oauth2 2.5.2（传统授权服务器）+ spring-security-jwt（JWT 令牌）
  - 各子系统的后端另用 RocketMQ Spring Boot Starter 2.2.3（消费 RBAC 下发的权限数据）
  - audit-backend 另用 RocketMQ Spring Boot Starter 2.2.3（消费 RBAC 权限快照 + 消费各系统上报的审计日志）
- **存储**：MySQL 8.0（业务数据，中枢与各子系统各自独立的库）+ Redis（登录会话 + OAuth2 授权流程的会话/跳转地址）
- **消息**：RocketMQ
  - `RBAC_SUBSYSTEM_SYNC`：中枢 → 子系统的权限下发通道（tag = 子系统编码）
  - `RBAC_AUDIT_LOG`：各系统 → 审计日志系统的增删改操作上报通道
- **前端**：Vue 3 / Vite 5 / Element Plus / Vue Router 4 / Pinia / axios

## 目录结构

```
springsecurity-RBAC/
├── rbac-backend/          # ① RBAC 后端（7070）
│   └── src/main/java/com/example/rbac/
│       ├── common/        # 统一返回、异常、状态码
│       ├── config/        # Security / Redis / MyBatis-Plus 配置
│       ├── aspect/        # @OperLog 操作日志切面
│       ├── security/      # OAuth2 令牌客户端与过滤器、权限快照、权限校验、超级管理员隐式权限
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
│                          #    新增 aspect/OperLogAspect：增删改经 MQ 上报审计系统
├── marketing-frontend/    # ⑧ 营销系统界面（5175）—— /api 代理到 7072
├── audit-backend/         # ⑨ 审计日志系统后端（7074，独立库 audit_log）
│   └── src/main/java/com/example/audit/
│       ├── common/        # 统一返回、异常、状态码
│       ├── config/        # Security / MyBatis-Plus / Jackson / OAuth2 客户端配置
│       ├── security/      # 令牌换用户名、权限判定（@ss）、超级管理员识别、401/403 处理器
│       ├── entity/        # audit_user / audit_menu / audit_role_menu / audit_user_role + audit_log
│       ├── mapper/        # MyBatis-Plus Mapper（含「按用户查权限点」的联查）
│       ├── service/       # 登录授权、MQ 快照重建、审计日志落库与查询
│       ├── controller/    # /auth/*、/audit/*
│       ├── mq/            # 消费权限快照（tag = audit）+ 消费审计日志（RBAC_AUDIT_LOG）
│       └── dto/ util/
├── audit-frontend/        # ⑩ 审计日志系统界面（5178）—— /api 代理到 7074
│   └── src/
│       ├── api/           # axios 实例（含 401 单飞刷新）与审计查询接口
│       ├── views/         # 登录跳转页、授权码回调、audit/index（筛选 + 请求参数抽屉）
│       └── src 其余目录    # store / router / layout / utils / directive（同其他子系统）
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
    ├── upgrade-2026-10-16-sso.sql             # SSO 客户端配置：portal 客户端 + 子系统客户端免确认页
    ├── upgrade-2026-10-17-remove-sso-menu.sql # 移除「SSO 会话」菜单（rbac-backend 已不提供该接口）
    ├── upgrade-2026-10-17-audit.sql           # 审计日志系统接入中枢：客户端 audit + 子系统 + 菜单权限
    ├── upgrade-2026-10-17-remove-subsystem-menus.sql    # 移除子系统管理/子系统菜单管理/子系统用户授权三个菜单入口
    ├── upgrade-2026-10-17-subsystem-create-permission.sql # 「菜单管理」里新增子系统的权限点
    ├── upgrade-2026-10-18-remove-subsystem-admin.sql    # 移除子系统内授权：删除管理员权限点与 subsystem_admin 标记
    ├── upgrade-2026-10-18-wecom-sso.sql                 # 企业微信扫码登录：身份绑定表 + 会话登录方式
    ├── upgrade-2026-10-18-wecom-qr-login.sql            # 企业微信扫码登录短链票据（DB + Redis）
    ├── upgrade-2026-10-18-idp-directory-sync.sql        # 外部部门与本地组织节点映射表
    ├── upgrade-2026-10-18-idp-directory-push.sql        # 外部通讯录推送事件（幂等）
    ├── upgrade-2026-10-18-idp-outbound-outbox.sql       # RBAC 反向推送企业微信/钉钉通讯录的 Outbox
    ├── upgrade-2026-10-18-user-profile.sql              # 用户宽表字段（工号/职务/性别等）+ 多部门关联表
    ├── order-schema.sql                       # 订单中心独立库 order_center（同步表 + order_info + 演示数据）
    ├── marketing-schema.sql                   # 营销系统独立库 marketing
    ├── audit-schema.sql                       # 审计日志系统独立库 audit_log（同步表 + audit_log）
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
mysql -uroot -p --default-character-set=utf8mb4 < sql/audit-schema.sql     # 审计日志系统库 audit_log
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-17-audit.sql # 审计系统接入中枢（只动 rbac 库）
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-17-remove-subsystem-menus.sql    # 收敛菜单入口
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-17-subsystem-create-permission.sql # 新增子系统权限点
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-remove-subsystem-admin.sql    # 移除子系统内授权
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-wecom-sso.sql                # 企业微信扫码登录
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-idp-directory-sync.sql       # 通讯录/部门同步
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-idp-directory-push.sql       # 通讯录推送事件
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-idp-outbound-outbox.sql      # 出站推送 Outbox
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-user-profile.sql             # 用户宽表字段 + 多部门
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-wecom-qr-login.sql           # 企业微信扫码登录短链票据
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
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-17-remove-sso-menu.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-17-audit.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-17-subsystem-create-permission.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-remove-subsystem-admin.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-wecom-sso.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-idp-directory-sync.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-idp-directory-push.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-idp-outbound-outbox.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-user-profile.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-wecom-qr-login.sql
```

> 前几个脚本是迭代过程留下的：先是独立部门表，再加独立分公司模块，最后把分公司并入部门树、换成岗位模块。中途的 `company` 脚本只挂载了一次独立分公司模块，随后被最后一个脚本合并回组织树并删除。**全新安装只需执行 `schema.sql` + `data.sql` + 三个子系统的 schema + `upgrade-2026-10-17-audit.sql`（把审计系统注册到中枢）。**
>
> 三个子系统的 `*-schema.sql` 都是 `DROP DATABASE` 重建，只在首次接入时执行；日常升级不碰它们（同步表的数据由 MQ 自己维护）。

Redis 默认连接 `localhost:6379`。子系统只连 Redis 缓存授权流程的 state（一次性、30 分钟 TTL）；业务数据仍只存在自己的 MySQL。

### 2. 准备 RocketMQ

子系统靠 RocketMQ 接收 RBAC 下发的权限数据，需要先启动 NameServer（9876）与 Broker（10911）：

```bash
mqnamesrv                                   # NameServer，默认 9876
mqbroker -n localhost:9876                  # Broker
```

topic `RBAC_SUBSYSTEM_SYNC` 与 `RBAC_AUDIT_LOG` 都由生产者自动创建，不需要手工建。
> 首次部署时建议**先启动 audit-backend、再让各系统上报**：RocketMQ 消费者第一次订阅一个刚被创建出来的 topic 时，默认从最新位点开始，可能跳过 topic 创建前的首批消息。生产环境可预先建好 `RBAC_AUDIT_LOG`。

> **Broker 没起来会怎样**：中枢照常运行，子系统也照常启动，但同步表是空的 —— 表现为「授权服务能认证通过，但本系统提示账号未同步（2004）」。这是预期行为：子系统的权限数据**只**来自 MQ。

### 3. 修改后端配置

五个后端的 `application.yml` 都要改，**其中几项中枢两服务必须一致**：

- `rbac-backend/src/main/resources/application.yml`
- `rbac-oauth/src/main/resources/application.yml`
- `order-backend/src/main/resources/application.yml`
- `marketing-backend/src/main/resources/application.yml`
- `audit-backend/src/main/resources/application.yml`

| 配置项 | 说明 |
|---|---|
| `oauth2.client-secret-key` | **中枢两服务必须相同**（子系统不需要）。rbac-backend 用它加密/解密客户端密钥（页面要展示明文给第三方），rbac-oauth 用它解密以校验客户端身份。一旦变更，库中已存的密文都解不开，需在「客户端管理」页面重设密钥 |
| MySQL 连接 | 中枢两服务是同一个库：`clientdetails` 由 rbac-backend 读写、rbac-oauth 读；`sys_user` 供授权服务的密码模式校验身份与开放接口的用户访问。**子系统各连各的库**（`order_center` / `marketing` / `audit_log`），与中枢库无任何交集 |
| Redis 连接 | 同一套 Redis（库号也要一致）：SSO 会话 `sso:session:*` 是本服务的登录态源头，rbac-backend 与各子系统靠撤权广播（`*:sso:revoked:*`）感知会话销毁；rbac-backend 另用自己的 `login:perm:user:*` 权限快照与 `rbac:bff-session:*` BFF 会话。子系统各自使用 `{system}:bff-session:*` 与 `{system}:sso:revoked:*`，不存业务数据 |
| `oauth2.client-id` / `client-secret` | 各系统自己的客户端身份，必须与中枢库 `clientdetails` 里的记录一致（RBAC 管理后台 `rbac-admin` / `rbac123`，订单中心 `order-center` / `order123`，营销系统 `marketing` / `marketing123`，审计系统 `audit` / `audit123`） |
| `oauth2.redirect-uri` | 各系统前端的回调页，必须与 `clientdetails.redirectUrl` **一模一样**，否则授权码模式换令牌会被拒（RBAC 后台 5173，订单中心 5176，营销系统 5175，审计系统 5178） |
| `oauth2.browser-authorize-base-url` | 用来拼**浏览器跳转**的授权地址（默认 `http://localhost:5174`，走授权前端的 `/oauth` 代理）。后端自己调令牌端点仍用 `oauth2.server-url`（7071） |
| 前端 `.env.development` | 各前端都要配 `VITE_OAUTH_SERVER_URL`（授权服务 7071）：未登录时跳它去登录、统一登出也整页跳向它。RBAC 后台为 5173，三个子系统前端为 5175/5176/5178 |
| `rocketmq.name-server` | 子系统必填（`localhost:9876`）。中枢的 rbac-backend 也用它做**生产者** |
| `rocketmq.producer.group` | **需要上报审计日志的系统必须配置**（`rbac-backend-producer` / `marketing-backend-producer` / `order-backend-producer`）。不配时 starter 不创建 `RocketMQTemplate`，审计上报会被静默跳过；audit-backend 只消费，不需要生产者组 |
| `rbac.sso.*`（rbac-oauth） | `kick-topic` / `kick-consumer-group`（强制下线 MQ）、`reconcile-interval-ms`（在线记录对账）、`session-idle-ttl-seconds`（会话空闲超时，默认 604800 秒，每次刷新续期）、`session-max-ttl-seconds`（会话绝对上限，默认 2592000 秒，登录时写入、永不延长）。Redis 实际 TTL 取两者较小值 |
| `wecom.*`（rbac-oauth） | 企业微信扫码登录配置：`enabled`、`corp-id`、`agent-id`、`secret`（支持 `ENC(...)` 密文，建议用环境变量/Vault）、`callback-url`（必须与企业微信后台登记一致且公网可达）。详见「多 IdP 登录」一节 |
| `wecom.qr.*`（rbac-oauth） | 扫码登录短链：`enabled`、`ttl-seconds`（二维码有效期）、`confirm-ttl-seconds`（手机确认后电脑端等待时间）、`public-base-url`（手机可访问的短链域名）、`short-path`（默认 `/q`）。票据同时写 MySQL `sys_qr_login_ticket` 与 Redis `oauth2:qr:login:{code}` |
| `wecom.oauth2-authorize-url`（rbac-oauth） | 手机扫码后的企业微信网页授权地址，默认 `https://open.weixin.qq.com/connect/oauth2/authorize`，使用 `snsapi_base` 授权码流程 |
| `idp.*`（rbac-oauth） | 多 IdP 策略：`self-bind-enabled`（未绑定时自助绑定）、`bind-page-url`、`mock-enabled`（仅测试）、`jit.*`（自动建号开关、默认部门/角色/用户名前缀）。生产必须保持 `mock-enabled=false`、按需开启 JIT |
| `idp.directory-sync.*`（rbac-backend） | 通讯录/部门同步：`enabled`、`providers`（wecom,dingtalk,mock，可多个）、`push-token`（推送共享密钥）、`mock-enabled`、`cron`、`root-dept-id`、`create-users`、`default-role-keys`、`username-prefix`，以及企业微信/钉钉的企业 ID 与 Secret |
| `idp.outbound.*`（rbac-backend） | **RBAC → 企业微信/钉钉** 反向推送：`enabled` 总开关、`providers`（`wecom`/`dingtalk`/`mock`，可多个）、`max-retries`、`process-interval-ms`；`wecom-corp-id` + `wecom-secret`、`dingtalk-app-key` + `dingtalk-app-secret`、`dingtalk-corp-id`（身份映射用企业 ID）。也可用环境变量 `IDP_OUTBOUND_ENABLED`、`IDP_OUTBOUND_PROVIDERS`、`WECOM_*`、`DINGTALK_*` 配置 |

`oauth2.*` 里的客户端密钥与回调地址必须与库中 `clientdetails` 一致；`oauth2.client-secret-key` 生产环境务必更换（两端同步）。

### 4. 启动五个后端

```bash
cd rbac-backend      && mvn spring-boot:run     # RBAC 业务：http://localhost:7070
cd rbac-oauth        && mvn spring-boot:run     # OAuth2 授权服务器：http://localhost:7071
cd order-backend  
   && mvn spring-boot:run     # 订单中心：http://localhost:7073
cd marketing-backend && mvn spring-boot:run     # 营销系统：http://localhost:7072
cd audit-backend     && mvn spring-boot:run     # 审计日志系统：http://localhost:7074
```

中枢两个无启动顺序要求（各自独立连库与 Redis）。**子系统要先等中枢起来、并且 RBAC 推过一次权限数据**，否则同步表为空、登录会提示账号未同步（2004）—— 让 RBAC 推一次最简单的办法是重启 rbac-backend（启动时全量下发一次），或在「菜单管理」页切换到对应子系统后保存一次菜单。

> 审计日志系统的消费者应先于各系统上报动作启动，避免 topic 首次创建时的位点问题（见「准备 RocketMQ」）。

### 5. 启动五个前端

```bash
cd rbac-frontend      && npm install && npm run dev    # 管理后台：http://localhost:5173
cd rbac-oauth-web     && npm install && npm run dev    # 授权流程页：http://localhost:5174
cd order-frontend     && npm install && npm run dev    # 订单中心：http://localhost:5176
cd marketing-frontend && npm install && npm run dev    # 营销系统：http://localhost:5175

cd audit-frontend     && npm install && npm run dev    # 审计日志系统：
```

- 管理后台 `http://localhost:5173`：`/api` 代理到 rbac-backend（7070）。**没有自己的登录表单**，未登录会跳到授权服务的登录页（5174），登录完经 `/oauth/callback` 回到本系统
- 授权流程页 `http://localhost:5174`：`/oauth` 原样代理到 rbac-oauth（7071）——授权码/隐式模式靠浏览器跳转与原生表单提交，必须与授权端点同源，Cookie 才带得上
- 订单中心 `http://localhost:5176`：`/api` 代理到 order-backend（7073）。**没有自己的登录页**，未登录会直接去授权服务的登录页（5174），登录完自动回到本系统
- 营销系统 `http://localhost:5175`：`/api` 代理到 marketing-backend（7072）。同上
- 审计日志系统 `http://localhost:5178`：`/api` 代理到 audit-backend（7074）。同样没有登录页，登录走同一套 SSO

> 四个系统统一采用 **BFF**：令牌保存在各自后端 Redis（`{system}:bff-session:*`），浏览器只有独立的 HttpOnly 会话 Cookie（`rbac_bff_session` / `order_bff_session` / `marketing_bff_session` / `audit_bff_session`），OAuth 令牌一律不下发。因此**五个前端仍可以同时开在同一个浏览器里互不干扰**。登录态则由 rbac-oauth 的会话 Cookie 共享：登录过一次之后，再访问另一个系统会**跳过登录页、也跳过授权确认页**（各系统客户端的 `autoApproveScopes` 已放行 `read`），直接落地。RBAC 管理后台（5173）同样适用：先登录订单中心，再打开后台也不需要再登录。

### 6. 默认账号

| 账号 | 密码 | 角色 | RBAC 后台 | 子系统（订单中心 / 营销系统） | 审计日志系统 |
|---|---|---|---|---|---|
| admin | admin123 | 超级管理员 | 全部权限 | 全部功能 | 全部功能 |
| zhangsan | admin123 | 普通用户 | 仅仪表盘 + 用户列表查询 | 两条只读页面（订单列表/订单统计、抽奖记录/积分流水），无任何写按钮 | 仅「操作日志」查询页（`audit:log:list` / `audit:log:query`） |

账号密码只有一套，存在中枢库 `sys_user` 里；子系统没有自己的用户表，登录时密码由授权服务器校验。

## 接口一览

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 认证 | GET | `/auth/sso-login` | 无（白名单）：拼接统一授权地址并 **302** 过去（前端只导航，地址里不含客户端密钥） |
| 认证 | POST | `/auth/code` | 无（白名单）：授权码模式，用回调带回来的 code + state 换令牌并建立 BFF 会话；state 一次性（Redis），重放返回 1032 |
| 认证 | POST | `/auth/logout` | 登出：销毁授权服务的 SSO 会话与本系统的 BFF 会话，并写操作日志 |
| 认证 | GET | `/auth/info` | 当前用户信息 + 权限 + 菜单树 |
| 认证 | DELETE | `/auth/kick/{userId}` | 强制用户下线（`system:user:edit`；禁止踢自己 1019，目标不存在 1003） |
| 认证 | PUT | `/auth/password` | 自助修改密码（校验原密码，**保留当前会话**，不会把自己踢下线） |
| 用户 | GET | `/user/page` | 分页查询（`system:user:list`） |
| 用户 | POST/PUT/DELETE | `/user`、`/user/{id}` | 增改删 |
| 用户 | PUT | `/user/{id}/status` | 设置状态：1 在职 / 0 停用 / 2 离职（停用与离职都立即失效登录凭证） |
| 用户 | PUT | `/user/{id}/roles` | 分配角色 |
| 用户 | PUT | `/user/{id}/resetPassword` | 管理员重置他人密码（**强制该用户下线**，需用新密码重新登录） |
| 用户 | GET | `/user/identities/providers` | 支持绑定的外部身份提供方（wecom / dingtalk / oidc / mock） |
| 用户 | GET | `/user/{userId}/identities` | 用户的外部身份绑定列表（`system:user:query`） |
| 用户 | POST | `/user/{userId}/identities` | 绑定外部身份（`system:user:edit`，写操作日志） |
| 用户 | DELETE | `/user/identity/{id}` | 解绑企业微信（`system:user:edit`，写操作日志） |
| 通讯录 | POST | `/idp/directory/sync` | 手动触发一次多来源拉取对账（`system:user:edit`） |
| 通讯录 | POST | `/idp/directory/events/{provider}` | 外部系统推送用户/部门变更（`X-Directory-Token`，幂等） |
| 角色 | GET | `/role/page`、`/role/all` | 分页 / 全部启用角色 |
| 角色 | PUT | `/role/{id}/menus` | **动态修改角色权限**（超级管理员角色返回 1028：其权限由代码隐式授予） |
| 部门 | GET | `/dept/tree` | 组织架构树：公司 / 分公司 / 部门（type 1/2/3），管理页与用户表单共用 |
| 部门 | POST/PUT/DELETE | `/dept`、`/dept/{id}` | 增改删（删除前校验下级节点与关联用户） |
| 岗位 | GET | `/post/page`、`/post/list` | 分页 / 全部启用岗位 |
| 岗位 | POST/PUT/DELETE | `/post`、`/post/{id}` | 增改删（删除前校验是否已被用户使用） |
| 菜单 | GET | `/menu/tree` | 菜单树 |
| 菜单 | POST/PUT/DELETE | `/menu`、`/menu/{id}` | 增改删 |
| 审计 | GET | `/audit/page` | **账号/会话类操作日志**分页查询（`system:audit:list`），支持用户名 / 模块 / 动作 / 结果 / 时间段。接口增删改不在这里，统一进审计日志系统 |
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
| `/oauth/sso-logout` | GET/POST | **统一登出**：删 SSO 会话（各子系统令牌即刻失效）+ 删登录态并让浏览器丢掉 Cookie。GET 版供整页跳转，302 回 `redirect_uri`（仅限已登记客户端的来源）；POST 版返回 JSON |
| `/oauth/test-callback` | GET | 授权码回调联调端点，**仅测试用**（`oauth2.test-callback.enabled=false` 可关闭） |
| `/oauth/wecom/authorize` | GET | 企业微信兼容入口（等价于 `/oauth/idp/wecom/authorize`） |
| `/oauth/wecom/callback` | GET | 企业微信兼容入口（等价于 `/oauth/idp/wecom/callback`） |
| `/oauth/idp/{provider}/authorize` | GET | 通用外部身份登录入口（provider = wecom / mock ...） |
| `/oauth/idp/{provider}/callback` | GET | 通用回调：code → 外部身份 → 绑定/JIT/自助绑定 → SSO 会话 |
| `/oauth/idp/bind` | POST | 自助绑定：`ticket + username + password`，验证本地密码后建立绑定并登录 |
| `/oauth/providers` | GET | 登录页可用的认证方式及启用状态 |
| `/openapi/userinfo` | GET | 令牌自检：返回该令牌的 subject / clientId / scopes |
| `/openapi/users` | GET | 用户列表，需要 scope `read` |
| `/openapi/users/{id}/status` | PUT | 修改用户状态，需要 scope `write` |

除了 `/openapi/**` 按 OAuth2 令牌的 scope 判权，本服务其余端点或对外开放（`/oauth/token_key`、`/oauth/check_token`），或走浏览器会话（授权流程）。**客户端管理不在这里**——它是后台管理功能，跑在 rbac-backend 上，按 `system:client:*` 权限点判权。

### 多 IdP 登录（企业微信 / Mock）

> 用户同步的完整设计（认证、Pull/Push、JIT、自助绑定、离职处理、数据模型与运维排查）见独立文档：[docs/user-sync.md](docs/user-sync.md)。

外部身份是**登录认证方式**，不是新的授权模式：授权码/令牌契约完全不变。多 IdP 通过 SPI 接入（`IdentityProvider`），新增钉钉/OIDC 只需实现接口。

```
第三方系统 → /oauth/authorize → 未登录 → 5174/login
  ├─ 账号密码 → /oauth/login ────────────────────────────┐
  └─ 企业微信 / Mock                                    │
       → /oauth/idp/{provider}/authorize（一次性 state）  │
       → 外部扫码页 / Mock 回调                          │
       → /oauth/idp/{provider}/callback                  │
       → 已绑定？ → 登录                                 │
         未绑定？ → JIT 自动建号 → 登录                  │
                  → 自助绑定页 /bind → 密码确认 → 登录   │
       → 建立 SSO 登录态 ───────────────────────────────┤
                                                         ↓
                         回到 /oauth/authorize → 发 code → 第三方换 token
```

**三种账号策略**

1. **管理员预先绑定**：「用户管理 → 行内『身份绑定』」选择提供方（企业微信/钉钉/OIDC/Mock），填写企业/租户 ID 与外部用户 ID；同一个用户可为多个平台分别绑定；
2. **自助绑定**（`idp.self-bind-enabled=true`，默认开启）：未绑定时跳 `/bind?ticket=...`，用户输入本地账号密码确认身份后建立绑定；ticket 5 分钟、最多尝试 5 次；
3. **JIT 自动建号**（`idp.jit.enabled=true`，默认关闭）：按配置自动创建本地用户，分配默认角色（`default-role-keys`）并绑定外部身份。

> 当前 rbac-oauth 已实现 `wecom` 与 `mock` 的登录适配；`dingtalk` / `oidc` 已作为可绑定提供方预留，接入时实现 `IdentityProvider` 即可，用户绑定数据无需迁移。

**通讯录 / 部门同步（rbac-backend，Push + Pull）**

- **Pull 对账**：`providers` 配置多个来源（`wecom,dingtalk,mock`），手动 `POST /idp/directory/sync` 或按 `cron` 定时执行；全量快照幂等 upsert，用于首次基线与丢事件补偿；
- **Push 增量**：`POST /idp/directory/events/{provider}`，请求头 `X-Directory-Token` 必须与 `idp.directory-sync.push-token` 一致；事件统一格式：
  ```json
  {
    "eventId": "evt-001",
    "eventType": "USER_CREATED",
    "externalUserId": "ding-user-003",
    "name": "Ding Three",
    "departmentIds": ["101"],
    "active": true
  }
  ```
  支持 `USER_CREATED/USER_UPDATED/USER_DELETED`、`DEPT_CREATED/DEPT_UPDATED/DEPT_DELETED`；
- **幂等**：`provider + event_id` 唯一（`sys_directory_event`），重复投递返回 `duplicate=true`；失败事件记录状态与原因；
- **删除语义**：外部成员删除/停用 → 本地用户置停用、解绑身份、踢下线并失效令牌；部门删除 → 本地部门置禁用，不物理删除；
- **多来源**：每个来源独立 `provider + corp_id + external_user_id` 映射，避免跨企业串号；部门映射存 `sys_dept_identity`；
- **企业微信/钉钉真实推送**：网关/适配层完成签名校验与解密后，转换成上面的 `DirectoryEvent` 再调用本接口；定时 Pull 继续保留作为对账兜底；
- 提供 `mock` 与 `mock dingtalk` 两套通讯录用于本地联调。

**反向推送：RBAC → 企业微信/钉钉（出站 Outbox）**

- 本地用户/部门新增、修改、删除时，在业务事务内写入 `sys_idp_outbox`，由 `IdpOutboxProcessor` 异步调用外部通讯录 API，失败按指数退避重试（默认最多 5 次）；
- 目标提供方由 `idp.outbound.providers` 决定，**企业微信** 使用 `wecom-corp-id` + `wecom-secret`，**钉钉** 使用 `dingtalk-app-key` + `dingtalk-app-secret`（企业内部应用，需通讯录管理权限）；
- 管理端可手动触发：`GET /idp/outbound/providers`、`POST /idp/outbound/sync-all?provider=wecom`（全量，先部门后用户）、`POST /idp/outbound/user/{userId}?provider=wecom|dingtalk`、`POST /idp/outbound/dept/{deptId}?provider=...`；用户管理页也提供「推送全部到企业微信」和行内「推送企微」按钮；
- 全量推送会把本地根部门映射到企业微信/钉钉根部门 1；企业微信已存在同 userid 自动转更新，同名同父部门自动复用；本地用户名不合法时依次使用工号、用户名，仍不合法则生成 `u{userId}`；
- **企业微信前置条件**：自建应用需开通通讯录管理权限，并把后端出口 IP 加入「应用管理 → 自建应用 → 企业可信IP」，否则会报 `60020 not allow to access from your ip`；
- 首次推送成功后自动写入 `sys_user_identity` / `sys_dept_identity`，后续按外部 ID 更新；
- 本地角色、权限、菜单、数据范围**永远不写回外部**；
- 出站与入站事件互不干扰：入站 `POST /idp/directory/events/{provider}` 仍是外部系统通知 RBAC 的入口。

```bash
# 启用反向推送（企业微信 + 钉钉）
IDP_OUTBOUND_ENABLED=true \
IDP_OUTBOUND_PROVIDERS=wecom,dingtalk \
WECOM_CORP_ID=ww...  WECOM_SECRET=... \
DINGTALK_APP_KEY=ding... DINGTALK_APP_SECRET=... \
IDP_OUTBOUND_DINGTALK_CORP_ID=ding... \
  mvn -f rbac-backend/pom.xml spring-boot:run
```

**Mock 测试方式**

```bash
# rbac-oauth：启用 mock 身份提供方
IDP_MOCK_ENABLED=true mvn -f rbac-oauth/pom.xml spring-boot:run

# rbac-backend：启用 mock + mock 钉钉通讯录（多来源）
IDP_DIRECTORY_SYNC_PROVIDERS=mock,dingtalk \
IDP_DIRECTORY_SYNC_MOCK_ENABLED=true \
IDP_DIRECTORY_SYNC_PUSH_TOKEN=test-token \
  mvn -f rbac-backend/pom.xml spring-boot:run
```

- `/oauth/providers` 会返回 `mock.enabled=true`；
- 访问 `/oauth/idp/mock/authorize?returnTo=/oauth/authorize?...` 可走完整登录链路；
- 未绑定时进入 `/bind`，用 admin/admin123 完成绑定；
- 开启 `IDP_JIT_ENABLED=true` 时，未绑定会自动建号；
- `POST /idp/directory/sync` 用 admin 令牌触发多来源对账；
- `POST /idp/directory/events/mock`（或 `dingtalk`）加 `X-Directory-Token: test-token` 可测试推送。

> **生产要求**：`idp.mock-enabled` 与 `idp.directory-sync.mock-enabled` 必须保持 `false`；JIT 默认关闭，启用前确认企业通讯录可信。

**安全与审计**

- state 一次性存 Redis（5 分钟），原子取出删除，防重放/CSRF；
- 只接受 `/oauth/...` 相对地址作为回跳目标，防开放重定向；
- Secret 不写日志、不下发前端；支持 AES-GCM 密文（`ENC(...)`）；
- 企业微信 access_token 缓存在 Redis，多实例用短锁避免并发刷新；
- 自助绑定必须验证本地密码，ticket 一次性、限次数；
- `sys_sso_session.login_type` 记录 `PASSWORD` / `WECOM` / `MOCK`，便于审计；
- 统一登出、强制下线、单会话模型对所有登录方式同样生效。

> **授权码存储**：授权码不再放内存，改为 Redis 键 `oauth2:code:{code}`（值为 `OAuth2Authentication` 的 JDK 序列化结果），默认 5 分钟 TTL，可用 `oauth2.authorization-code-ttl-seconds` 调整。兑换令牌时用 Lua 脚本原子 `GET + DEL`，保证一次性消费；多实例部署与授权服务重启都不会丢未兑换的授权码。

已登记的三个子系统客户端（密钥在库里是 AES 密文，这里是明文）：

| appId | 明文密钥 | grantTypes | redirectUrl | scope |
|---|---|---|---|---|
| `order-center` | `order123` | authorization_code, password, refresh_token | http://localhost:5176/oauth/callback | read,write |
| `marketing` | `marketing123` | authorization_code, password, refresh_token | http://localhost:5175/oauth/callback | read,write |
| `audit` | `audit123` | authorization_code, refresh_token | http://localhost:5178/oauth/callback | read |

> 子系统**只申请 `read` 这一个 scope**（见各自 `application.yml` 的 `oauth2.scope`）：令牌端点的 `scope` 按 RFC 6749 是以**空格**分隔的，写成 `read,write` 会被当成一个名叫 `read,write` 的范围而报 `invalid_scope`。子系统不按 scope 判权，用的是 RBAC 下发的权限点，所以申请一个 `read` 就够。

### 子系统后端（order-backend `:7073` / marketing-backend `:7072`）

订单中心与营销系统的接口形状完全一致，把权限点前缀换掉即可（订单中心 `order:*`，营销系统 `marketing:*`）。以订单中心为例：

| 模块 | 方法 | 路径 | 权限点 |
|---|---|---|---|
| 登录 | POST | `/auth/login` | 无（白名单）：密码模式，账号密码转交 rbac-oauth 校验 |
| 登录 | POST | `/auth/code` | 无（白名单）：授权码模式，用回调带回来的 code + state 换令牌；state 一次性（Redis），重放返回 2020 |
| 登录 | GET | `/auth/sso-login` | 无（白名单）：拼接授权地址并 **302** 过去（前端只导航，地址里不含客户端密钥） |
| 登录 | POST | `/auth/logout` | 无（白名单）：销毁授权服务的 SSO 会话、删除本系统 BFF 会话并清 Cookie |
| 登录 | GET | `/auth/info` | 需登录：返回身份 + 角色 + 权限点 + 菜单树 |
| 订单 | GET | `/order/page` | `order:order:list` |
| 订单 | GET | `/order/{id}` | `order:order:query` |
| 订单 | POST/PUT | `/order` | `order:order:add` / `order:order:edit` |
| 订单 | DELETE | `/order/{id}` | `order:order:remove` |
| 订单 | GET | `/order/stat` | `order:stat:list`（只读统计：总数、金额合计、按状态分布） |

子系统**没有登录页的账号体系、也没有用户表**：`/auth/*` 只是把请求转给授权服务器，落库的只有从 RBAC 同步来的用户/菜单/角色数据。

### 审计日志系统后端（audit-backend `:7074`）

审计系统同样是一个标准子系统：SSO 登录 + 权限点由 RBAC 经 MQ 下发，另加一层**审计消息消费**。

| 模块 | 方法 | 路径 | 权限点 |
|---|---|---|---|
| 登录 | POST | `/auth/code` | 无（白名单）：授权码模式换令牌（**不提供密码模式**）；code + state，state 一次性（Redis），重放返回 2020 |
| 登录 | GET | `/auth/sso-login` | 无（白名单）：拼接授权地址并 302 |
| 登录 | POST | `/auth/logout` | 无（白名单）：销毁授权服务的 SSO 会话、删除本系统 BFF 会话并清 Cookie |
| 登录 | GET | `/auth/info` | 需登录：身份 + 角色 + 权限点 + 菜单树 |
| 审计 | GET | `/audit/page` | `audit:log:list`：分页查询，支持 `source` / `module` / `action` / `operatorName` / `success` / `startTime` / `endTime` |
| 审计 | GET | `/audit/{id}` | `audit:log:query`：单条详情（含完整 `requestParams`） |

查询接口只读，不提供修改与删除；数据只来自 MQ 上报。

## 核心功能验证步骤

### 1. 无感知续期

> 四个系统统一采用 BFF：令牌保存在各自后端 Redis（`{system}:bff-session:*`），浏览器只有 HttpOnly 会话 Cookie。Access Token 过期后由后端用 Refresh Token 静默续期，前端与浏览器都不接触令牌；并发请求由后端用 Redis 锁做「单飞」，保证同一张一次性 Refresh Token 只提交一次。登录后可在 DevTools 中确认 Cookie 带 `HttpOnly`、`localStorage` 里没有任何令牌。

> 快速验证：把 `clientdetails` 里某个客户端的 `access_token_validity` 临时改成 30 秒，
> 重新登录后等 30 秒再点菜单：请求仍然成功（后端已静默续期），
> Redis 里对应的 `{system}:bff-session:*` 记录中 `accessToken` / `refreshToken` 已换成新值。
> 改完记得改回 3600。

### 2. 离职立刻失效

1. 用 zhangsan 在浏览器 A 登录，能正常访问仪表盘。
2. 用 admin 在浏览器 B 的「用户管理」中点击 zhangsan 的**离职**（也可点**停用**）。
3. 回到浏览器 A 点击任意菜单，请求立即返回 401 并跳转登录页——无需等 Token 过期。
4. 各系统手里的旧 access token 下一次请求即被拒绝，刷新令牌也换不出新的（SSO 会话已删除）。
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

**增删改部分（已迁移）**

3. 登录、下线、改密等**账号/会话类动作**仍可在 RBAC 后台「操作日志」页看到。
4. 岗位、用户、角色等接口的**增删改**不再写入 `sys_operation_log`，而是上报到独立的审计日志系统 —— 验证方法见下一节「9. 审计日志系统端到端」。这样两边不重复，且审计侧能记录请求参数。

**筛选与权限**

7. 按模块、动作、结果、时间段筛选，结果随之收窄。
8. 用 zhangsan 访问 `/audit/page` 返回 403 —— 账号/会话日志只对有 `system:audit:list` 的角色开放。

### 5. 子系统菜单与超级管理员

**子系统菜单（RBAC 统一给子系统配菜单）**

1. 用 admin 打开「系统管理 → 子系统管理」，能看到「订单中心」与「营销系统」，也可新增子系统（编码唯一，重复报 1025）。
2. 打开「系统管理 → 菜单管理」，顶部「所属系统」下拉默认是 RBAC 本系统；切到某个子系统后，表格会加载该子系统的菜单树，新增/修改/删除都直接作用于 `sys_subsystem_menu`。每个子系统都有**唯一编码**（`order-center` / `marketing` / `audit`），菜单保存后经 MQ 只下发给这个编码对应的子系统。三个层级的 `perms` 都参与子系统侧的接口鉴权；按钮节点只作权限点、`visible=0` 不在导航里显示。
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

   1. 打开下面的地址（授权端点按标准只认 `client_id`，不接收 `client_secret`）：

      ```
      http://localhost:7071/oauth/authorize?response_type=code&client_id=rbac-third-party&redirect_uri=http://localhost:7071/oauth/test-callback&scope=read&state=abc
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

1. 打开 `http://localhost:5176`：未登录，直接落到授权服务的登录页（`http://localhost:5174/login`，子系统自己没有登录页），用 admin / admin123 登录。
2. 登录成功后浏览器自动回到订单中心（授权端点凭会话发码 → `/oauth/callback` 换令牌），侧边栏只有「订单管理 → 订单列表 / 订单统计」两项（`order_menu` 表里下发的菜单树），仪表盘上「我的权限标识」显示 `*:*:*`。
3. 再打开 `http://localhost:5175`（营销系统）：**不需要再登录、也看不到登录页**。浏览器里已有授权服务的登录态，营销系统的 `/login` 去自己的授权端点，授权端点凭会话直接发码，静默把它送进系统。
4. 反过来把登录态清掉（换个无痕窗口）直接打开业务页，会被送到授权服务的登录页 —— 这就是「子系统没有登录页、登录由授权服务承担」的表现。

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

### 8. SSO 会话与统一登出端到端

前提：已执行 `sql/upgrade-2026-10-16-sso.sql`、`sql/upgrade-2026-10-17-remove-sso-menu.sql`，`rbac-oauth`(7071) 与各子系统的前后端均已启动。

**登录与静默换令牌**

1. 打开 `http://localhost:5176`：未登录 → 授权服务的登录页（`http://localhost:5174/login`）→ admin / admin123 登录 → 自动回到订单中心。
2. 再打开 `http://localhost:5175`：**不要求登录，登录页也不出现**（授权端点凭会话直接发码）。
3. 用 Redis 看这次登录存了什么：

   ```bash
   redis-cli keys 'sso:*'
   # sso:session:admin   {"username":"admin",...,"clients":["order-center","marketing"]}
   # sso:browser:admin   浏览器登录态的 token
   redis-cli keys 'oauth2:auth:*'
   # oauth2:auth:{token} {"userId":1,"username":"admin","nickname":"超级管理员","loginTime":"..."}
   ```

   `rbac-oauth` 与 `rbac-backend` **重启不会把用户踢下线**：签名密钥来自 `jwt.jks`（不是启动时随机生成），会话在 Redis 里。想手工清场就 `redis-cli del sso:session:admin sso:browser:admin sso:uid:1`（再删掉对应 `oauth2:auth:*`）。

**统一登出（核心）**

4. 在订单中心点「退出登录」→ 停在授权服务的登录页；`sso:session:admin`、`sso:browser:admin`、`oauth2:auth:*` 都被删掉了，待授权请求缓存（`oauth2:reqcache:*` 与 `RBAC_OAUTH2_REQ` Cookie）也一并清理。
5. **旧令牌下一请求即失效**：

   ```bash
   curl -H "Authorization: Bearer <登出前的旧令牌>" http://localhost:7073/order/stat
   # 401（授权服务的 check_token 发现 SSO 会话已不存在，判为无效令牌）
   ```

6. **刷新令牌也换不出新令牌**（否则登出会被绕过）：

   ```bash
   curl -u order-center:order123 -d grant_type=refresh_token \
        -d refresh_token=<登出前的旧刷新令牌> http://localhost:7071/oauth/token
   # 400 invalid_grant：登录会话已结束，请重新登录
   ```

7. **登录态也一起没了**：直接打开 `http://localhost:7071/oauth/authorize?response_type=code&client_id=order-center&redirect_uri=http://localhost:5176/oauth/callback&scope=read` → 会被要求重新登录（若登录态还在，这里会直接发码）。
8. **跨浏览器**：两个浏览器各登录一次同一个账号，在其中一个登出 —— 另一个的令牌**立刻 401**（SSO 会话按**用户**存，与浏览器无关）。另一个浏览器里那份 Cookie 不会被物理删除，但它指向的登录态已经不存在，无法再静默登录。
9. **跨标签页**：把订单中心与营销系统各开一个标签页，在营销那边登出，再**切回订单中心的标签页** —— 会被拦到登录页（靠切回时的惰性校验），不用等你去点下一个按钮。
10. **两种登录方式都试一遍**：子系统的授权码登录（唯一入口）与第三方直接调密码模式，登出链路对两者都必须生效。密码模式那条尤其容易漏 —— 浏览器里没有授权服务的登录态，登出请求只能靠前端那一步「带令牌的 fetch」认人（见下文第 2 条）。

**不受影响的流程**

11. `client_credentials` 令牌照常可用，且**不受统一登出影响**（没有用户，也就没有 SSO 会话）：`curl -u rbac-third-party:client123 -d grant_type=client_credentials http://localhost:7071/oauth/token` 得到的令牌，调 `/openapi/userinfo` 仍返回 200。
12. RBAC 管理后台自身也走统一 SSO（`rbac-admin` 客户端）：后台「退出登录」会先写操作日志，再调用 rbac-oauth 的统一登出删除 SSO 会话 —— 后台与所有子系统一起下线，不再是与 SSO 相互独立的第二套登录态。

### 9. 审计日志系统端到端

前提：已执行 `sql/audit-schema.sql` 与 `sql/upgrade-2026-10-17-audit.sql`；RocketMQ 已启动；`audit-backend`(7074) 与 `audit-frontend`(5178) 已启动；RBAC 至少下发过一次权限（`audit_user` / `audit_menu` / `audit_role_menu` 非空）。

**只记录增删改，不记录登录**

1. 用 admin 在营销系统新增 → 修改 → 删除一个抽奖活动；在订单中心新增 → 修改 → 删除一单；在 RBAC 后台新增 → 修改 → 删除一个用户。
2. 查询独立库：

   ```sql
   SELECT source, module, action, operator_name, http_method, request_uri, success, request_params
   FROM audit_log.audit_log ORDER BY id;
   ```

   应看到 9 条记录，`source` 分别为 `marketing` / `order-center` / `rbac`，`action` 为 `CREATE/UPDATE/DELETE`。
3. 确认登录/登出**不在**审计表里：

   ```sql
   SELECT COUNT(*) FROM audit_log.audit_log WHERE action IN ('LOGIN','LOGOUT');  -- 0
   ```

   而 RBAC 的 `sys_operation_log` 仍能看到刚才的 `LOGIN` 记录。

**请求参数与脱敏**

4. 新增用户那条记录的 `request_params` 包含 `username`、`nickname`、`deptId` 等字段，**`password` 的值是 `***`**（切面递归脱敏，`password/pwd/secret/token/authorization/credential` 等键都会被掩码）。
5. 删除类记录的 `request_params` 是路径变量，例如 `15`（`DELETE /user/15` 的目标 ID）。
6. 参数超过 8000 字符时按 `TEXT` 容量截断。

**审计前端**

7. 打开 `http://localhost:5178`：未登录会走与订单中心/营销系统相同的 SSO 登录页（5174），登录后回到审计系统。
8. 侧边栏只有「审计日志 → 操作日志」。可按来源系统、模块、动作、操作人、结果、日期筛选；点行尾「请求参数」打开抽屉，`requestParams` 会格式化为缩进后的 JSON。
9. 用 zhangsan 登录：只有 `audit:log:list` / `audit:log:query`，能查询但没有写入口（审计系统本身也没有写接口）。
10. 不带令牌调用 `GET http://localhost:7074/audit/page` 返回 401。

**多来源聚合（核心）**

11. 三个来源系统各自实现自己的 `@OperLog` 切面，消息字段名相同但代码不共享；都发到同一个 topic：

    ```
    rbac-backend / marketing-backend / order-backend
        └─ AuditLogPublisher → RBAC_AUDIT_LOG → audit-log-consumer → audit_log.audit_log
    ```

    消息体字段：`source / module / action / description / operatorId / operatorName / httpMethod / requestUri /
    requestParams / success / errorMsg / durationMs / ip / timestamp`。来源由 `source` 区分，不需要按 tag 订阅。
12. 业务失败（例如重复编码新增）也会上报一条 `success=0` 的记录，`errorMsg` 是异常信息；MQ 不可用时只记 error 日志，**不影响业务成功**。

## SSO 会话与统一登出

这一节讲清楚「登录一次，进第二个子系统不用再登录」以及「任一入口登出，所有子系统立即失效」是怎么实现的。

### 要解决的问题

RBAC 授权服务器签发的 access token 是**自包含 JWT**（RSA 签名，不落库）。好处是任何服务拿公钥就能验签、不用回源；代价是**签出去就收不回来**——服务端手里没有它的任何记录，因此「登出」在纯 JWT 方案里根本无处落地。原来的子系统登出只是清掉 localStorage，服务端令牌照旧有效直到过期。

### 解法：给令牌套一层服务端会话

在令牌之外加一层 Redis **SSO 会话**，把令牌绑到会话上：

```
sso:session:{username}   SSO 会话 JSON，存在 = 令牌可用                TTL = min(空闲超时, 绝对上限剩余)
sso:browser:{username}   该用户浏览器登录态的 token                     同上
sso:uid:{userId}         userId → username 反查索引                    同上
oauth2:auth:{token}      授权服务的登录态（精简认证信息，见下）           TTL 30 分钟
oauth2:code:{code}       未兑换的授权码（OAuth2Authentication 序列化）   TTL 默认 300 秒（oauth2.authorization-code-ttl-seconds）
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
| 统一登出 | 删 SSO 会话 + 删登录态 | `SsoLogoutController` / `OauthAuthStore` |

「删会话即下线」这套做法最初来自 rbac-backend 管理端自签令牌时代的会话模型（该后台现已并入统一 SSO），这里把作用域从「一个系统」放大到「所有系统」。

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

   - 于是前端两步都做（见 `order-frontend` / `marketing-frontend` 的 `utils/sso.js`）：
     1. `fetch POST /oauth/sso-logout`，带 `Authorization: Bearer <本系统令牌>`（授权服务一侧已配好带凭证的 CORS）—— 靠它**认人并删掉 SSO 会话**；
     2. 整页跳转 `GET /oauth/sso-logout?redirect_uri=...` —— 靠它凭 Cookie 认出登录态、删掉 `oauth2:auth:{token}`、写一个过期的 Cookie 把浏览器那份丢掉，并顺手把用户带到登录页。

   - **为什么不能只做第 2 步（这是实际踩到的坑）**：密码模式登录的用户，浏览器里根本没有授权服务的登录态 —— 密码是子系统后端带客户端凭证去换的令牌，浏览器全程没参与。跳转请求既没 Authorization 头也没 Cookie，授权服务认不出「是谁在登出」，SSO 会话原封不动，于是**从营销系统登出后，订单中心仍然进得去**。
   - **为什么不能只做第 1 步**：被踢下线的标签页手里已经**没有有效令牌**了，它的 fetch 带不出能认人的东西；只有整页跳转那份 Cookie 还能让授权服务认出「是谁」。少了这一步，Redis 里的 `oauth2:auth:{token}` 就清不掉，用户回到授权页仍是登录状态。

3. **刷新令牌要拒绝已登出的人。** `refreshAccessToken` 里必须用 `touch`（只在键已存在时延长 TTL）而不是 `createOrRenew`：后者会把登出时删掉的会话重新建出来，等于给登出留了个后门。

4. **子系统的 `check_token` 缓存必须去掉。** 原来每个请求缓存 5 分钟（「令牌在有效期内身份不会变」），那会让「用户已在别处登出」最长滞后 5 分钟生效。现在每次请求实时校验，代价是授权服务侧一次本地验签 + 一次 Redis GET，不查库。

5. **`client_credentials` 令牌不受影响。** 它没有用户、没有 SSO 会话，统一登出管不着它 —— 这是期望行为：机器身份不该因为某个人的登录状态变化而失效。

6. **跨源的子系统不会「被告知」登出，靠惰性校验补上体感。** 服务端是**立即**失效的，但各子系统跑在不同的源（5175 / 5176）上，彼此之间没有任何通道（localStorage / BroadcastChannel 都按源隔离）。默认表现是「下次请求才被踢回登录页」—— 用户切回订单中心、只是看着不点任何东西的话，看到的还是登录状态的页面，很容易以为登出没生效。所以各系统前端都在布局里挂了 `utils/sessionWatch.js`：**切回本标签页（`visibilitychange` 变可见，或用 `focus` 兜住两窗口并排的情况）时，悄悄请求一次登录态接口**。请求走同一个 axios 实例，因此复用了既有的处理链 —— 令牌过期由后端静默续期（用户无感），会话已结束则清 Cookie 跳登录页。
   - 为什么不用轮询：定时轮询会在用户根本没看这个页面时也持续发请求，而「切回标签页」这个时机已经覆盖了绝大多数会产生「怎么还登录着」疑问的场景，代价却是零。
   - 同一轮 `visibilitychange` + `focus` 会用 1 秒节流折叠成一次请求。

### 子系统的登录入口：直接去授权服务的登录页

子系统**没有登录页**：`/login` 保留着，但它不是表单，而是一个跳转页（路由守卫、401 拦截器、登出回跳都指向 `/login`，保留这个路径它们就都不用改）。

```
未登录访问 5176
  → /login 导航到后端 GET /auth/sso-login（后端拼授权地址并 302，前端不接触密钥）
  → 授权端点发现没登录 → 302 到 http://localhost:5174/login
  → 输账号密码 → 302 回那次授权 → 凭会话发码 → /oauth/callback 存令牌 → 落地
```

**落点统一**：主动打开子系统、点「退出登录」、被踢下线，最终都停在**授权服务的登录页** `http://localhost:5174/login`。

落点必须**带着待处理的授权请求**：浏览器是经由 `/oauth/authorize` 被 302 到 5174 的，会话里缓存着「哪个客户端要授权、码发到哪」。如果直接把浏览器丢到 5174/login（会话里没有待授权的请求），用户输完账号密码会被弹回登录页 —— 5174 的 `/` 就是重定向到 `/login`，等于登不进去。所以「子系统登出」的回跳地址是**本系统自己的 `/login`**，由它再走一遍授权流程。

这套设计有个附带好处：**已经登录过的人不会看到登录页**。授权端点先查会话，有会话就静默发码，直接落进子系统 —— 「登录一次，进第二个子系统免登录」靠的就是它。

三个设计取舍：

- **授权地址只由后端生成**：子系统用 `GET /auth/sso-login`，由后端拼好地址并 302；前端只做一次导航。地址里只有 `client_id / redirect_uri / scope / state`，**不含 `client_secret`** —— 密钥只用于后端调令牌端点时的 HTTP Basic 认证。
- **没有登录页意味着出错时可能「无落脚点」**，所以取授权地址失败时会停在跳转页给出原因与「重试」按钮，而不是白屏或跳到一个空地址。
- **登出只有点登出的那个标签页会立刻跳走**；其他标签页要等下一次请求（或切回来时的惰性校验）才发现自己已被踢，然后同样落到 5174/login。

### 相关接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `rbac-oauth:7071/oauth/sso-logout?redirect_uri=` | 统一登出（整页跳转），清 SSO 会话 + 登录态后 302 回 `redirect_uri`（只接受已登记客户端的来源，否则回授权服务登录页） |
| POST | `rbac-oauth:7071/oauth/sso-logout` | 同上，XHR 版，返回 JSON |

### 管理员动作触发的 SSO 强制下线

管理员在 RBAC 后台的以下动作会在本地事务提交后发一条 MQ 消息到 `RBAC_OAUTH_SESSION`，由 rbac-oauth 的 `SsoKickConsumer` 删除 `sso:session:{username}`、`oauth2:auth:{token}`，并关闭 `sys_sso_session` 里的在线记录：

| 管理员动作 | 接口 | 消息 `type` | 下线原因 |
|---|---|---|---|
| 停用账号 | `PUT /user/{id}/status?status=0` | `DISABLED` | 账号已停用 |
| 标记离职 | `PUT /user/{id}/status?status=2`（或编辑用户时改状态） | `RESIGNED` | 账号已离职 |
| 强制下线 | `DELETE /auth/kick/{userId}` | `KICK` | 被管理员强制下线 |
| SSO 会话页强制下线 | `POST /sso/session/kick/{sessionId}` | `KICK` | 被强制下线 |
| 删除用户 | `DELETE /user/{id}` | `DELETE` | 账号已删除 |
| 重置密码 | `PUT /user/{id}/resetPassword` | `RESET_PWD` | 密码已重置 |

- 消息体：`type / sessionId / userId / username / reason / timestamp`，消费方按 `username` 定位会话（与浏览器无关，一次踢掉该用户所有终端）。
- 发送在事务 **提交之后**（`SsoKickPublisher` 的 `afterCommit`），避免业务回滚却把人踢下线；MQ 未启动或发送失败只记 error 日志，不影响管理操作本身。
- 若本地 `sys_sso_session` 没有在线记录（例如用户从未通过授权页登录），消费方仍然会删 Redis 会话，`closeOnlineRows` 幂等返回 0。

## 子系统接入

「子系统」= 一个独立库 + 独立后端 + 独立前端的业务系统，**自己不做登录、不做权限管理**，把这两件事整体交给 RBAC。订单中心、营销系统与审计日志系统都是按这套模式接入的，接入步骤可直接照抄。

### 认证：统一走 rbac-oauth，登录页就是授权服务的登录页

子系统不存密码、**没有自己的登录页**：未登录访问时直接去授权服务的登录页（`http://localhost:5174/login`），登录完再回到本子系统。

```
未登录访问 5176
  → 子系统的 /login（不是表单，是个跳转页）
  → 导航到后端 GET /auth/sso-login：由后端拼好授权地址并 302（只有 client_id / redirect_uri / scope / state）
  → 授权端点发现没登录 → 302 到授权服务的登录页 http://localhost:5174/login
  → 输入账号密码 → 302 回刚才那次授权 → 凭会话直接发码（autoApproveScopes 已放行 read）
  → 订单中心 /oauth/callback 用 code 换令牌（令牌保存在子系统后端 Redis，浏览器只拿 HttpOnly 会话 Cookie）→ 落地在订单中心
```

**前端全程不接触授权地址**：它由后端拼接、由后端发 302，前端只做一次「导航」。地址里不含客户端密钥，密钥只用于后端调令牌端点；以前那个「把授权地址作为 JSON 返回给前端」的接口（`/auth/authorize-url`）已经下线。

已经登录过（浏览器里还有授权服务的会话）时，第二步之后的登录页**根本不会出现**：授权端点直接发码，用户就静默落进子系统 —— 「登录一次，进第二个子系统免登录」靠的就是这个会话。

| 模式 | 谁在用 | 链路 |
|---|---|---|
| 授权码模式 | **子系统前端唯一的方式** | 见上面的流程图 |
| 密码模式 | 第三方接入 / curl 联调（子系统前端不再使用） | 直接调子系统 `/auth/login` → 子系统带客户端凭证调 rbac-oauth `/oauth/token`（`grant_type=password`）→ 返回令牌 |

> 密码模式为什么从子系统前端撤掉：那个流程是「子系统后端带客户端凭证去换令牌」，**浏览器全程没参与**，因此浏览器里不会留下授权服务的登录态。这让统一登出多出一整类特例（纯跳转式登出认不出密码模式登录的用户，详见下文「SSO 会话与统一登出」第 2 条）。所有子系统登录都走授权码后，浏览器一定有登录态，登出链路只剩一种形态。
> 后端的密码模式与 `/auth/login` 仍然保留，供第三方接入与联调使用。

子系统采用 **BFF**：后端换到的令牌只写进本服务的 Redis（`{system}:bff-session:{sessionId}`），浏览器拿到的是一个 HttpOnly 会话 Cookie（`{system}_bff_session`）。之后每个请求带 Cookie，子系统后端从会话里取 access token **本地验签**（公钥来自 `/oauth/token_key`），**仅凭用户名**去本地的同步表找用户与权限 —— 令牌本身不携带权限；令牌过期时由后端用同一会话里的 refresh token 静默续期。

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
- **消息体**：`subsystemCode` + `menus` + `roleMenus` + `userRoles` + `users` 五份**全量**列表（不是增量）。其中「全量」是指**该子系统范围内的全量**：
  - `menus` / `roleMenus` 只包含本子系统的菜单与角色授权；
  - `users` 只包含「拥有本子系统授权角色的用户」+ 超级管理员（超管授权不落库，靠 `role_keys` 识别，且需要能进入每个子系统）；
  - `userRoles` 只包含上述用户与本子系统授权角色的关联。
  没有在本子系统配置任何角色的用户不会下发，也无法登录该子系统（2004 账号未同步）。
- **触发范围**：用户增删改、用户角色变更时，按**旧角色 ∪ 新角色**反查这些角色在哪些子系统有菜单授权，只给这些子系统发消息；角色启停/标识变更/删除同理（超管角色变化则发全部）。rbac-backend 启动时仍会向所有启用子系统各发一次，用于首次接入与兜底。
- **触发时机**：用户增删改与角色变更、角色-子系统菜单授权变更、子系统菜单增删改，以及 rbac-backend **应用启动时全量下发一次**。发送在事务提交之后，避免「消息发了但事务回滚了」。
- **消费语义**：子系统收到后在一个事务里**清空重建** 4 张同步表（`order_user` / `order_menu` / `order_role_menu` / `order_user_role`）。全量快照 + 清空重建比逐条 diff 简单可靠，也不会因漏处理某条删除而留下过期授权。
- **超管不落库**：`admin` 的授权在中枢与子系统都不落表。子系统靠同步下来的 `role_keys` 里是否含 `admin` 来识别，识别到就直接给通配符 `*:*:*` 与全部启用菜单。

### 判权：每个请求实时查同步表

```
HttpOnly Cookie → 会话里的 AT → 本地验签 → user_name → order_user
           → order_role_menu ⋈ order_user_role ⋈ order_menu.perms
           → @PreAuthorize("@ss.hasPerm('order:order:add')")
```

同步表就是权限的唯一事实来源，所以中枢改一次授权，子系统**下一个请求**就生效 —— 不需要重新登录，也不需要重启。

**角色权限变更经 MQ 实时下发**：

| 中枢操作 | 下发的 MQ 消息 | 已登录子系统用户的效果 |
|---|---|---|
| 角色管理 → **分配子系统菜单**（`PUT /role/{id}/subsystem-menus`） | `RBAC_SUBSYSTEM_SYNC`，只发对应子系统 | 子系统重建菜单/授权表，下一个请求即按新权限判定，**无需重新登录** |
| 用户角色变更、用户增删改 | `RBAC_SUBSYSTEM_SYNC`，**只发受影响子系统** | 按旧角色 ∪ 新角色的授权反查子系统；消息内只带该子系统相关的用户与角色 |
| 角色启停、角色标识修改或删除 | `RBAC_SUBSYSTEM_SYNC`，只发该角色授权覆盖的子系统（超管角色变化发全部） | 各子系统重新识别有效期角色与 `role_keys`（含超管识别） |
| RBAC 后台自身的「分配权限」（`PUT /role/{id}/menus`） | 不发 MQ | 只影响 RBAC 管理后台的 `@PreAuthorize` 判定：失效该角色用户的本地权限快照，下一次请求重建。子系统不使用这套菜单 |

> 说明：SSO 用户在各子系统的权限**不保存在 rbac-oauth 里**，令牌也不携带权限。权限的事实来源是子系统本地由 MQ 重建的同步表，因此「角色权限动态生效」靠的是上面的 `RBAC_SUBSYSTEM_SYNC`，而不是给授权服务发下线消息。

### 在 RBAC 后台新增子系统

1. 用拥有 `system:subsystem:add` 的账号（超管默认拥有）打开「系统管理 → 菜单管理」；
2. 点顶部「新增子系统」，填写：
   - **子系统名称**：展示用，如「营销系统」；
   - **子系统编码**：唯一标识，只能包含字母、数字、下划线和短横线（如 `marketing`）。该编码同时用作 MQ tag，创建后不要随意修改；
   - 排序、状态、备注可选。
3. 保存后会出现在「所属系统」下拉中，并自动切换过去；接着就能按目录 / 菜单 / 按钮维护它的菜单树。
4. 该入口只登记 RBAC 侧的子系统与菜单。要让新服务真正可用，还需要：
   - 在 `clientdetails` 登记对应的 OAuth2 客户端（appId 建议与编码一致）；
   - 部署按「接入一个新子系统」改造的新后端与前端。

### 接入一个新子系统要改哪些地方

1. 中枢库里建子系统与菜单树：`sys_subsystem` 一行 + `sys_subsystem_menu` 若干行（目录/菜单/按钮，带 `perms`）+ `sys_role_subsystem_menu` 给角色授权。
2. 中枢库里登记 OAuth2 客户端：`clientdetails` 一行（appId、AES 密文密钥、grantTypes、**redirectUrl 必须与前端回调页完全一致**）。
3. 新库：`*-schema.sql` 建 4 张同步表（结构照抄 `order-schema.sql`，表名前缀换掉）+ 自己的业务表。
4. 新后端：照抄 `order-backend` —— 改包名、库名、端口、`oauth2.client-id/client-secret/redirect-uri`、`SUBSYSTEM_CODE`、消费组名，以及权限点前缀；同步消费需要重建 4 张同步表，权限查询使用「用户角色 ⋈ 角色菜单」。
5. 新前端：照抄 `order-frontend` —— 改端口、`/api` 代理目标、`VITE_OAUTH_SERVER_URL`、品牌文案；业务页的目录路径必须与 `sys_subsystem_menu.component` 一一对应。登录相关三块照抄即可，不用自己实现：
   - `views/login/index.vue` —— 不是表单，是「导航到后端 `/auth/sso-login`（由它 302 到授权服务登录页）」的页面；
   - `views/oauth/callback.vue` —— 用 code 建立 BFF 会话；
   - `utils/sso.js` / `utils/sessionWatch.js` —— 统一登出与「切回标签页校验登录态」。
6. （可选）接入审计日志：照抄任一上报端（`marketing-backend` 或 `order-backend`）的 `common/OperLog`、`aspect/OperLogAspect`、`mq/AuditLogMessage`、`mq/AuditLogPublisher`、`util/AuditParamUtils` 与 `util/WebUtils`，改包名并把 `AuditLogPublisher.SOURCE` 设为本系统的来源标识；给 CRUD 接口加 `@OperLog(module, action, description)`；配置 `rocketmq.producer.group`。登录/登出接口**不要**加注解。审计系统侧无需改动，`source` 直接按新值落库。

## 拆分后的共享约定

中枢两个后端是独立的 Spring Boot 进程，但**共用同一个 MySQL 库与同一套 Redis**；子系统则各自独立一个库，只通过 **MQ 与授权服务器**与中枢发生关系（Redis 只放本系统自己的数据：授权 state、登录用户/权限快照、撤权标记与 BFF 会话，键名按系统前缀隔离）。跨进程的耦合点只有下面几处，改动时需两边同步：

- **客户端密钥**：`clientdetails.appSecret` 在库里是 AES 密文，中枢两服务都用 `oauth2.client-secret-key` 加解密 —— rbac-backend 写入时加密、回显时解密（页面要展示明文给第三方），rbac-oauth 解密以校验客户端身份。**两处配置必须一致**，否则客户端会全部认证失败。子系统不需要这个配置项（它们只持有自己的明文密钥）。
- **Redis 键**：`login:perm:user:{username}`（权限快照，由 rbac-backend 独占维护）只在本系统内使用。登录态本身在 rbac-oauth 的 `sso:session:*` 里；第三方经 `/openapi` 停用/离职用户时，rbac-oauth 直接删除该用户的 SSO 会话（同时兜底关闭 `sys_sso_session` 在线记录），后台与所有子系统即刻下线。子系统只把 Redis 用于授权 state —— 它的「会话」仍然是授权服务器签发的令牌。
- **SSO 会话键（全部由 rbac-oauth 自己维护）**：`sso:session:{username}`（会话本体，存在=令牌可用）、`sso:browser:{username}`（该用户浏览器登录态的 token）、`sso:uid:{userId}`（userId → username 反查）、`oauth2:auth:{token}`（登录态本体，精简 JSON）、`oauth2:code:{code}`（未兑换的授权码，默认 5 分钟 TTL）。rbac-backend 不读写这批键 —— 统一登出只由授权服务负责，细节见「SSO 会话与统一登出」一节。
- **库表**：`clientdetails` 由 rbac-backend 读写（客户端管理），rbac-oauth 只读（加载客户端做认证）；`sys_user` 两边都读 —— rbac-oauth 用它做密码模式的身份校验与开放接口的用户访问。子系统**不读中枢的任何表**。
- **MQ 下发契约**：topic `RBAC_SUBSYSTEM_SYNC`、tag = 子系统编码、消息体字段名（`subsystemCode` / `menus` / `roleMenus` / `users` / `userRoles`）。中枢的生产者（`SubsystemSyncService`）与子系统的消费者（`SubsystemSyncConsumer` + `*-SyncService`）是**两份各自独立的代码**，字段名或语义变更必须两边同步改 —— 这里没有共享库或共享 DTO 来兜住。见「子系统接入」。
- **授权服务里没有 RBAC**：rbac-oauth 不解析后台的登录令牌，也没有权限快照与 `@PreAuthorize`。要改客户端配置只能走 rbac-backend 的「客户端管理」页（`system:client:*` 权限 + 操作日志）。
- **操作日志与审计日志**：`sys_operation_log` 由 rbac-backend 独占写入，**只保留账号/会话类动作**（登录、登出、踢下线、停用/启用/离职、重置密码、自助改密）；接口增删改不再写这张表。客户端配置的增删改也不再写它，改为上报审计日志系统。**唯一不再留痕的是**第三方经 `/openapi/users/{id}/status` 停用/离职用户（原本会记一条 `DISABLED`/`RESIGNED`，**强制下线本身仍然生效**）。不把日志表与切面复制到授权服务，是为了避免两处实现漂移；若要审计第三方的动作，应在授权服务侧补一个精简写入。
- **MQ 强制下线契约**：topic `RBAC_OAUTH_SESSION`，消息字段 `type / sessionId / userId / username / reason / timestamp`。生产者是 rbac-backend（`SsoKickPublisher`），消费者是 rbac-oauth（`SsoKickConsumer`，消费组 `rbac-oauth-session-consumer`）。两边各持一份 `SsoKickMessage`，`type` 取值 `KICK / DISABLED / RESIGNED / DELETE / RESET_PWD` 需同步维护。
- **MQ 审计上报契约**：topic `RBAC_AUDIT_LOG`（不分 tag），消息体字段名见「审计日志系统端到端」。生产者分布在 rbac-backend / marketing-backend / order-backend（各自一份 `AuditLogMessage` + `AuditLogPublisher` + `AuditParamUtils` + 切面），消费者在 audit-backend（`AuditLogConsumer` + `AuditLogService`）。同样没有共享 DTO，字段名变更需各服务同步。生产端发送在业务事务提交之后，失败只记日志不影响业务；消费端解析失败丢弃、落库失败抛出让容器重试。

## 说明与注意事项

- **两个有效期**：Access Token 与 Refresh Token 的有效期都登记在 `clientdetails`（`access_token_validity` 默认 30 分钟、`refresh_token_validity` 默认 7 天），由 rbac-oauth 签发时决定。四个系统的 Access Token 过期后都由后端静默续期（BFF，浏览器不接触令牌）。真正的准入判断是 SSO 会话 `sso:session:{username}`（空闲超时默认 7 天、绝对上限默认 30 天，见 `rbac.sso.session-*`），所以删除会话（登出/停用/离职/踢下线）能立即生效，而不必等 Token 过期。各系统的 BFF 会话另有自己的空闲 7 天 + 绝对 7 天上限（`rbac.bff.session-*`）。
- **Refresh Token 一次性使用**：每次刷新都会换发新值，旧值立即作废（携带 sid 绑定的 `sso:refresh-sid:{sha256(token)}` 被原子消费，再提交即 `invalid_grant`）。Redis 里只存令牌的 SHA-256，不存令牌本身；`sso:refresh-sid:user:{username}` 记录该用户名下所有绑定，登出或会话轮换时一并清理，不会残留到 TTL。**旧的 Access Token 不受影响**，仍在自己的 `exp` 前可用 —— 这是 JWT 无状态校验的预期行为，缩小窗口靠短的有效期而非逐令牌撤销。多标签页并发刷新时，后端用 Redis 锁做「单飞」：只有一个请求真正去换令牌，其余请求复用结果，因此不会因为并发提交同一张一次性 Refresh Token 触发复用检测。
- **授权 state 防重放**：`/auth/sso-login` 生成随机 state 写入 Redis（`oauth2:state:{clientId}:{state}`，30 分钟 TTL），**同时下发 HttpOnly、SameSite=Lax 的 `RBAC_OAUTH_STATE_{CLIENT}` Cookie**；回调页从地址栏取出 state 后随 `{code, state}` 提交给 `/auth/code`。服务端先要求请求体 state 与 Cookie 一致（防登录 CSRF：别人塞进受害者浏览器的 code+state 没有对应 Cookie），再用 Lua 原子消费 Redis 记录（防重放）；成功后立即清除 Cookie。过期、伪造、跨浏览器或重放一律拒绝（RBAC 1032 / 子系统 2020）。state 由后端生成，前端不参与生成。
- **登录快照与失效**：用户信息、权限、角色在首次建立登录态时固化为快照（`login:perm:user:{username}`，TTL 7 天），之后每个请求直接由快照重建认证主体，不再访问数据库。为保证「改了立刻生效」，以下操作会**精准失效**受影响用户的快照，使其下一次请求重建：角色授权变更（失效该角色下全部用户）、用户资料修改、用户角色变更、强制下线、停用/离职、重置密码。只有菜单树仍实时查库，因此改菜单名称立即反映。
  **唯一的例外**：用 SQL 直接改 `sys_menu` / `sys_role_menu`（如执行增量脚本）绕过了失效逻辑，已登录用户读到的仍是旧快照，需重新登录，或按 [upgrade-2026-10-13-audit.sql](sql/upgrade-2026-10-13-audit.sql) 顶部的说明清一次 `login:perm:*`。
- **密码散列不进快照**：`SysUser.password` 标注了 `@JsonIgnore`，快照里没有它。需要校验密码的场景（自助改密）因此单独查一次库 —— 顺带保证校验的是「库里此刻的散列」，而不是会话建立时的旧值。
- **停用/离线的即时性靠删会话，不靠快照**：用户被停用或标记离职时删掉的是 Redis 会话，认证过滤器比对会话时就把请求挡在门外，所以即便快照里的 `status` 是登录时的旧值，也依然立刻下线。
- **逻辑删除与唯一键**：`sys_user` / `sys_role` / `sys_post` 的删除是**逻辑删除**（`del_flag=1`），行仍物理留在表里，唯一键也仍被占用。所以删除时会先把业务唯一字段改名为 `原名_deleted_{id}`（见 [UniqueKeyUtils](rbac-backend/src/main/java/com/example/rbac/util/UniqueKeyUtils.java)），否则被删掉的那个用户名 / 角色标识 / 岗位编码将永远无法再创建 —— 应用层的重名校验会放行（它排除了已删行），随后 INSERT 撞上数据库唯一键，用户只看到「系统异常」。若你的库里有更早版本的已删行，执行 [upgrade-2026-10-13-release-unique-key.sql](sql/upgrade-2026-10-13-release-unique-key.sql) 补上改名。
- **两条改密路径**：管理员在「用户管理」里重置他人密码（`PUT /user/{id}/resetPassword`）会**强制该用户下线**，适用于账号被盗或密码遗忘；用户自己在右上角「修改密码」（`PUT /auth/password`）需校验原密码，改完**当前会话继续有效**，不会被自己踢下线。两条路径都会写进操作日志（`RESET_PWD` / `CHANGE_PWD`）。
- **操作日志的写入时机**：`sys_operation_log` 只追加不修改、不提供删除接口。登录/登出/下线/改密由业务代码**在事务内**直接写，写失败会让该次操作一并失败；审计日志由各系统的切面在业务结果确定后经 MQ 上报，**上报失败不影响业务**（业务已经生效，不能因为审计系统暂时不可用把结果改成失败）。
- **审计日志记录请求参数，但先脱敏**：审计系统只记录接口的增删改操作，`request_params` 来自 controller 方法参数（`@RequestBody` DTO + `@PathVariable`），写入前递归把 `password / pwd / secret / token / authorization / credential` 等键的值替换为 `***`，并按 8000 字符截断。**不记录响应体**（体积大且可能含敏感数据，`success` / `errorMsg` / `durationMs` 已能表达执行结果）。RBAC 侧仅保留请求方法 + 路径（且不含查询串），因为密码重置等接口把密码放在查询串里。
- **ip 字段**：优先取 `X-Forwarded-For` 首段（经反向代理时），否则取 `remoteAddr`；该值客户端可控，已截断到字段长度以内。
- **超级管理员**：权限**不落库**，由代码隐式授予（[SuperAdmin](rbac-backend/src/main/java/com/example/rbac/security/SuperAdmin.java)）：权限集合是通配符 `*:*:*`，菜单树直接返回全部启用菜单，因此新增菜单后无需给 `admin` 补授权，`sys_role_menu` / `sys_role_subsystem_menu` 里也没有它的记录（授权接口对它返回 1028）。代价是它无法被收窄 —— 永远是全集。内置 `admin` 角色与 `admin` 用户不允许删除。
- **单会话模型**：一个用户同时只有一个有效会话，后登录会顶掉先前的会话。
- **子系统的判权与中枢不同，别照搬心智模型**：中枢把「用户+权限+角色」在登录时固化成 Redis 快照，之后每请求读快照、不查库；子系统**没有快照**，每个请求都从同步表实时联查权限点。因此中枢改授权后靠「精准失效快照」生效，子系统则天然即时生效、无需任何失效动作。反过来，子系统多了一次「用户表 + 权限点联查」的开销，换来的是任何时刻都绝不会用到过期权限。
- **子系统不受中枢的单会话约束**：单会话是中枢 Redis 会话带来的性质；子系统只验令牌，同一账号在多个浏览器/多台机器可并存（令牌各自独立过期）。
- **MQ 下发是幂等的**：消息体是全量快照、消费语义是清空重建，所以重复投递、乱序投递都不会把数据搞乱 —— 最后一条消息就是最终状态。代价是每次下发都要重写整张同步表，菜单与用户规模大时应改为增量（当前规模下不值得）。
- **子系统里的唯一键同样要「腾」**：`order_info.order_no`（以及营销侧的 `activity_code` / `task_code`）都是**逻辑删除**，删除时会把被占用的编号改名为 `原名_deleted_{id}`，否则那个编号永远无法再使用。机制与中枢一致，见 [UniqueKeyUtils](order-backend/src/main/java/com/example/order/util/UniqueKeyUtils.java)。
- **登录页停留超时不会丢授权上下文**：授权请求缓存（Redis `oauth2:reqcache:*` + Cookie `RBAC_OAUTH2_REQ`）只有 30 分钟。用户若在登录页停留超过这个时间，缓存会过期；为此未登录入口点会把原始授权地址作为 `returnTo` 交给登录页（`http://localhost:5174/login?returnTo=...`），登录页提交表单时原样带回，成功处理器在缓存为空时据此跳回 `/oauth/authorize`，因此等待多久都能回到子系统。`returnTo` 在服务端只接受以 `/oauth/` 开头的相对路径，绝对地址、`//host` 与 CRLF 一律拒绝，避免开放重定向；登录失败跳回 `?error=1` 时前端用 `sessionStorage` 保留该值。
- **授权码模式的 state 已做一次性校验与浏览器绑定**：`/auth/sso-login` 由后端生成 state（Redis 30 分钟 TTL），并下发 HttpOnly、SameSite=Lax 的 `RBAC_OAUTH_STATE_{CLIENT}` Cookie；回调页把地址栏里的 state 随 `{code, state}` 提交给 `/auth/code`，服务端先比对 Cookie 再用 Lua 原子消费 Redis 记录，成功后清除 Cookie。伪造、跨浏览器、过期与重放一律拒绝（RBAC 1032 / 子系统 2020）。
