# RBAC 权限管理后台

基于 **Spring Boot + Spring Security + MySQL + Redis + Vue 3** 的前后端分离 RBAC 权限管理系统。

## 核心能力

| 需求 | 实现方式 |
|---|---|
| **无感知续期** | Access Token 短有效期（默认 15 分钟），过期后前端拦截 401，用 Refresh Token 自动调用 `POST /auth/refresh` 换取新的 Access Token 并重放原请求（并发 401 只刷新一次）。用户全程无感知，不需要重新登录。 |
| **离职立刻失效凭证** | 用户状态三值：**1 在职 / 0 停用 / 2 离职**。会话保存在 Redis（`login:session:{userId}` + `login:refresh:{sessionId}`）。标记停用或离职时删除会话键，配合认证过滤器的会话校验，存量 Access Token 在下一次请求即被拒绝，旧 Refresh Token 也无法再续期，无需等待自然过期。 |
| **登录快照（用户 + 权限 + 角色）** | 用户信息、权限、角色在**登录时**查库一次，整体序列化写入 Redis（`login:perm:{sessionId}`）。之后每个请求都由快照重建认证主体，**不再访问数据库** —— 省掉「用户-角色-菜单」的多表联查与逐请求的用户行查询。 |
| **动态修改角色权限** | 管理员在角色授权页调整「角色-菜单」映射后，服务端立即**失效该角色下所有用户的权限快照**，用户下一次请求重建快照并拿到新权限，无需重新登录。 |
| **操作日志** | 全部落库到 `sys_operation_log`：登录、登出、强制下线、停用/启用/离职、重置密码、自助改密，以及用户/角色/菜单/部门/岗位的**新增、修改、删除、授权**。写入类操作由 `@OperLog` 注解 + AOP 切面自动记录（含模块、说明、请求方法路径、成败、耗时、来源 IP），查询类接口不记，避免日志表被翻页请求淹没。管理端「操作日志」页可按用户名、模块、动作、结果、时间段检索。 |

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
│       ├── entity/        # 9 张表的实体
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
    ├── schema.sql                             # 建表脚本（9 张表）
    ├── data.sql                               # 初始化数据
    ├── upgrade-2026-10-13-dept.sql            # 旧库升级①：部门模块
    ├── upgrade-2026-10-13-company.sql         # 旧库升级②：分公司模块（已被③合并回组织树）
    ├── upgrade-2026-10-13-org-post.sql        # 旧库升级③：分公司并入部门树 + 岗位模块
    ├── upgrade-2026-10-13-audit.sql           # 旧库升级④：登录/下线审计日志
    ├── upgrade-2026-10-13-operlog.sql         # 旧库升级⑤：审计日志扩展为通用操作日志（表改名）
    └── upgrade-2026-10-13-release-unique-key.sql  # 数据修复：释放被逻辑删除行占用的唯一键
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
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-audit.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-operlog.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-13-release-unique-key.sql
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
| 认证 | DELETE | `/auth/kick/{userId}` | 强制用户下线（`system:user:edit`；禁止踢自己 1019，目标不存在 1003） |
| 认证 | PUT | `/auth/password` | 自助修改密码（校验原密码，**保留当前会话**，不会把自己踢下线） |
| 用户 | GET | `/user/page` | 分页查询（`system:user:list`） |
| 用户 | POST/PUT/DELETE | `/user`、`/user/{id}` | 增改删 |
| 用户 | PUT | `/user/{id}/status` | 设置状态：1 在职 / 0 停用 / 2 离职（停用与离职都立即失效登录凭证） |
| 用户 | PUT | `/user/{id}/roles` | 分配角色 |
| 用户 | PUT | `/user/{id}/resetPassword` | 管理员重置他人密码（**强制该用户下线**，需用新密码重新登录） |
| 角色 | GET | `/role/page`、`/role/all` | 分页 / 全部启用角色 |
| 角色 | PUT | `/role/{id}/menus` | **动态修改角色权限** |
| 部门 | GET | `/dept/tree` | 组织架构树：公司 / 分公司 / 部门（type 1/2/3），管理页与用户表单共用 |
| 部门 | POST/PUT/DELETE | `/dept`、`/dept/{id}` | 增改删（删除前校验下级节点与关联用户） |
| 岗位 | GET | `/post/page`、`/post/list` | 分页 / 全部启用岗位 |
| 岗位 | POST/PUT/DELETE | `/post`、`/post/{id}` | 增改删（删除前校验是否已被用户使用） |
| 菜单 | GET | `/menu/tree` | 菜单树 |
| 菜单 | POST/PUT/DELETE | `/menu`、`/menu/{id}` | 增改删 |
| 审计 | GET | `/audit/page` | 操作日志分页查询（`system:audit:list`），支持用户名 / 模块 / 动作 / 结果 / 时间段 |

接口鉴权使用 `@PreAuthorize("@ss.hasPerm('system:user:list')")`，权限来自用户所属角色的菜单权限标识。

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
- **超级管理员**：`admin` 角色的权限同样存在 `sys_role_menu` 中，可动态调整。请谨慎取消其角色管理相关权限，以免无法再次授权。内置 `admin` 角色与 `admin` 用户不允许删除。
- **单会话模型**：一个用户同时只有一个有效会话，后登录会顶掉先前的会话。
