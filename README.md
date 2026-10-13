# RBAC 权限管理后台

基于 **Spring Boot + Spring Security + MySQL + Redis + Vue 3** 的前后端分离 RBAC 权限管理系统。

## 核心能力

| 需求 | 实现方式 |
|---|---|
| **无感知续期** | Access Token 短有效期（默认 15 分钟），过期后前端拦截 401，用 Refresh Token 自动调用 `POST /auth/refresh` 换取新的 Access Token 并重放原请求（并发 401 只刷新一次）。用户全程无感知，不需要重新登录。 |
| **离职立刻失效凭证** | 用户状态三值：**1 在职 / 0 停用 / 2 离职**。会话保存在 Redis（`login:session:{userId}` + `login:refresh:{sessionId}`）。标记停用或离职时删除会话键，配合认证过滤器的会话校验，存量 Access Token 在下一次请求即被拒绝，旧 Refresh Token 也无法再续期，无需等待自然过期。 |
| **权限登录快照** | 权限与角色在**登录时**查库一次并写入 Redis（`login:perm:{sessionId}`），之后每个请求直接读快照，省掉「用户-角色-菜单」的多表联查。 |
| **动态修改角色权限** | 管理员在角色授权页调整「角色-菜单」映射后，服务端立即**失效该角色下所有用户的权限快照**，用户下一次请求重建快照并拿到新权限，无需重新登录。 |

## 技术栈

- **后端**：Spring Boot 2.7.18 / JDK 8 / Spring Security 5.7 / MyBatis-Plus 3.5 / jjwt 0.11.5 / Maven
- **存储**：MySQL 8.0（业务数据）+ Redis（登录会话）
- **前端**：Vue 3 / Vite 5 / Element Plus / Pinia / Vue Router 4 / axios

## 目录结构

```
springsecurity-RBAC/
├── rbac-backend/          # Spring Boot 后端
│   └── src/main/java/com/example/rbac/
│       ├── common/        # 统一返回、异常、状态码
│       ├── config/        # Security / Redis / MyBatis-Plus 配置
│       ├── security/      # JWT、会话存储、权限校验、过滤器
│       ├── entity/        # 5 张表的实体
│       ├── mapper/        # MyBatis-Plus Mapper
│       ├── service/       # 业务逻辑
│       ├── controller/    # 接口
│       ├── dto/           # 请求/响应对象
│       └── util/          # 菜单树工具
├── rbac-frontend/         # Vue 3 前端
│   └── src/
│       ├── api/           # axios 实例（含无感刷新）与各模块接口
│       ├── store/         # Pinia：用户信息、动态路由
│       ├── router/        # 静态路由 + 路由守卫 + 动态 addRoute
│       ├── layout/        # 后台布局与递归菜单
│       └── views/         # 登录、仪表盘、用户/角色/菜单管理
└── sql/
    ├── schema.sql                             # 建表脚本（8 张表）
    ├── data.sql                               # 初始化数据
    ├── upgrade-2026-10-13-dept.sql            # 旧库升级①：部门模块
    ├── upgrade-2026-10-13-company.sql         # 旧库升级②：分公司模块（已被③合并回组织树）
    └── upgrade-2026-10-13-org-post.sql        # 旧库升级③：分公司并入部门树 + 岗位模块
```

## 快速开始

### 1. 准备数据库与 Redis

全新初始化（Windows 下务必带 `--default-character-set=utf8mb4`，否则中文会因客户端字符集报 `Data too long` 假错）：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/data.sql
```

已有的旧库按文件名顺序执行增量脚本升级（脚本一次性执行，重复执行会因 `ALTER` 报错）：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-dept.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-company.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-org-post.sql
```

> 三个脚本是迭代过程留下的：先是独立部门表，再加独立分公司模块，最后把分公司并入部门树、换成岗位模块。中途的 `company` 脚本只挂载了一次独立分公司模块，随后被最后一个脚本合并回组织树并删除。**全新安装只需执行 `schema.sql` + `data.sql`。**

Redis 默认连接 `localhost:6379`。

### 2. 修改后端配置

编辑 `rbac-backend/src/main/resources/application.yml`，确认 MySQL 账号密码与 Redis 地址，并按需更换 `jwt.secret`（生产环境务必更换，长度 ≥ 32 字节）。

### 3. 启动后端

```bash
cd rbac-backend
mvn spring-boot:run
```

服务地址：`http://localhost:7070`

### 4. 启动前端

```bash
cd rbac-frontend
npm install
npm run dev
```

访问：`http://localhost:5173`（`/api` 已代理到后端 7070）

### 5. 默认账号

| 账号 | 密码 | 角色 | 权限 |
|---|---|---|---|
| admin | admin123 | 超级管理员 | 全部权限 |
| zhangsan | admin123 | 普通用户 | 仅仪表盘 + 用户列表查询 |

## 接口一览

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 认证 | POST | `/auth/login` | 登录，返回 Access Token + Refresh Token |
| 认证 | POST | `/auth/refresh` | 用 Refresh Token 换取新的 Access Token（Access 过期时前端自动调用） |
| 认证 | POST | `/auth/logout` | 登出，删除会话 |
| 认证 | GET | `/auth/info` | 当前用户信息 + 权限 + 菜单树 |
| 认证 | DELETE | `/auth/kick/{userId}` | 强制用户下线 |
| 用户 | GET | `/user/page` | 分页查询（`system:user:list`） |
| 用户 | POST/PUT/DELETE | `/user`、`/user/{id}` | 增改删 |
| 用户 | PUT | `/user/{id}/status` | 设置状态：1 在职 / 0 停用 / 2 离职（停用与离职都立即失效登录凭证） |
| 用户 | PUT | `/user/{id}/roles` | 分配角色 |
| 用户 | PUT | `/user/{id}/resetPassword` | 重置密码（同时强制下线） |
| 角色 | GET | `/role/page`、`/role/all` | 分页 / 全部启用角色 |
| 角色 | PUT | `/role/{id}/menus` | **动态修改角色权限** |
| 部门 | GET | `/dept/tree` | 组织架构树：公司 / 分公司 / 部门（type 1/2/3），管理页与用户表单共用 |
| 部门 | POST/PUT/DELETE | `/dept`、`/dept/{id}` | 增改删（删除前校验下级节点与关联用户） |
| 岗位 | GET | `/post/page`、`/post/list` | 分页 / 全部启用岗位 |
| 岗位 | POST/PUT/DELETE | `/post`、`/post/{id}` | 增改删（删除前校验是否已被用户使用） |
| 菜单 | GET | `/menu/tree` | 菜单树 |
| 菜单 | POST/PUT/DELETE | `/menu`、`/menu/{id}` | 增改删 |

接口鉴权使用 `@PreAuthorize("@ss.hasPerm('system:user:list')")`，权限来自用户所属角色的菜单权限标识。

## 三个核心需求的验证步骤

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

## 说明与注意事项

- **两个有效期**：`jwt.access-token-expire`（默认 15 分钟）是 Access Token 的轮换周期，过期后由前端调 `/auth/refresh` 续期；`jwt.refresh-token-expire`（默认 7 天）既是 Refresh Token 的有效期，也是 Redis 会话的有效期 —— 即登录态的真正上限。JWT 的 `exp` 只决定「该续期了」，真正的准入判断是 Redis 会话，所以删除会话（离职/停用/踢下线）能立即生效，而不必等 Token 过期。
- **权限快照与失效**：权限与角色在登录时固化为会话快照（`login:perm:{sessionId}`，TTL 与 Refresh Token 一致），请求期间不再联表查询。为保证「改了立刻生效」，以下操作会**精准失效**受影响用户的快照，使其下一次请求重建：角色授权变更（失效该角色下全部用户）、用户角色变更、以及会话删除（登出/停用/离职/踢下线/重置密码，随会话一并清理）。快照只缓存权限与角色，用户资料与菜单树仍实时查库，因此改昵称、改部门、改菜单名称都能立即反映。
- **超级管理员**：`admin` 角色的权限同样存在 `sys_role_menu` 中，可动态调整。请谨慎取消其角色管理相关权限，以免无法再次授权。内置 `admin` 角色与 `admin` 用户不允许删除。
- **单会话模型**：一个用户同时只有一个有效会话，后登录会顶掉先前的会话。
