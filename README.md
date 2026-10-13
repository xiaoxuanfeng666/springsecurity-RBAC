# RBAC 权限管理后台

基于 **Spring Boot + Spring Security + MySQL + Redis + Vue 3** 的前后端分离 RBAC 权限管理系统。

## 核心能力

| 需求 | 实现方式 |
|---|---|
| **登录后无感知续期** | 双 Token：Access Token（15 分钟）+ Refresh Token（7 天）。前端 axios 响应拦截器在收到 401 时自动调用刷新接口并重放原请求，用户全程无感；并发请求通过单例 Promise 合并为一次刷新。 |
| **离职立刻失效凭证** | 会话保存在 Redis（`login:session:{userId}`）。禁用/删除用户（或强制下线）时删除会话键，配合认证过滤器的会话校验，存量 Access Token 在下一次请求即被拒绝，无需等待自然过期。 |
| **动态修改角色权限** | 用户权限**不做登录快照**，每次请求都从数据库联表实时加载。管理员在角色授权页调整「角色-菜单」映射后，该角色用户下一次请求立即生效，无需重新登录。 |

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
    ├── schema.sql         # 建表脚本
    └── data.sql           # 初始化数据
```

## 快速开始

### 1. 准备数据库与 Redis

```bash
mysql -uroot -p < sql/schema.sql
mysql -uroot -p < sql/data.sql
```

Redis 默认连接 `localhost:6379`。

### 2. 修改后端配置

编辑 `rbac-backend/src/main/resources/application.yml`，确认 MySQL 账号密码与 Redis 地址，并按需更换 `jwt.secret`（生产环境务必更换，长度 ≥ 32 字节）。

### 3. 启动后端

```bash
cd rbac-backend
mvn spring-boot:run
```

服务地址：`http://localhost:8080`

### 4. 启动前端

```bash
cd rbac-frontend
npm install
npm run dev
```

访问：`http://localhost:5173`（`/api` 已代理到后端 8080）

### 5. 默认账号

| 账号 | 密码 | 角色 | 权限 |
|---|---|---|---|
| admin | admin123 | 超级管理员 | 全部权限 |
| zhangsan | admin123 | 普通用户 | 仅仪表盘 + 用户列表查询 |

## 接口一览

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 认证 | POST | `/auth/login` | 登录，返回双 Token |
| 认证 | POST | `/auth/refresh` | 刷新 Access Token |
| 认证 | POST | `/auth/logout` | 登出，删除会话 |
| 认证 | GET | `/auth/info` | 当前用户信息 + 权限 + 菜单树 |
| 认证 | DELETE | `/auth/kick/{userId}` | 强制用户下线 |
| 用户 | GET | `/user/page` | 分页查询（`system:user:list`） |
| 用户 | POST/PUT/DELETE | `/user`、`/user/{id}` | 增改删 |
| 用户 | PUT | `/user/{id}/status` | 启用/禁用（禁用即失效登录凭证） |
| 用户 | PUT | `/user/{id}/roles` | 分配角色 |
| 用户 | PUT | `/user/{id}/resetPassword` | 重置密码（同时强制下线） |
| 角色 | GET | `/role/page`、`/role/all` | 分页 / 全部启用角色 |
| 角色 | PUT | `/role/{id}/menus` | **动态修改角色权限** |
| 菜单 | GET | `/menu/tree` | 菜单树 |
| 菜单 | POST/PUT/DELETE | `/menu`、`/menu/{id}` | 增改删 |

接口鉴权使用 `@PreAuthorize("@ss.hasPerm('system:user:list')")`，权限来自用户所属角色的菜单权限标识。

## 三个核心需求的验证步骤

### 1. 无感知续期

1. 用 admin 登录，进入任意页面。
2. 打开浏览器开发者工具 Network，等待 Access Token 过期（默认 15 分钟）。
3. 期间/之后继续点击任意菜单发起请求，可见一次 `POST /auth/refresh` 成功后原请求被自动重放。
4. 页面不会跳转到登录页，用户无感知。

> 快速验证：把 `application.yml` 中 `jwt.access-token-expire` 改为 `60000`（1 分钟）后重启，即可在 1 分钟内观察到刷新过程。

### 2. 离职立刻失效

1. 用 zhangsan 在浏览器 A 登录，能正常访问仪表盘。
2. 用 admin 在浏览器 B 的「用户管理」中把 zhangsan 禁用。
3. 回到浏览器 A 点击任意菜单，请求立即返回 401 并跳转登录页——无需等 Token 过期。
4. 即使拿旧的 refreshToken 调 `/auth/refresh` 也会被拒绝（会话已删除）。

### 3. 动态修改角色权限

1. 用 admin 在「角色管理」中点击「普通用户」的**分配权限**。
2. 勾选「用户管理」下的「用户新增」等按钮权限，保存。
3. 让 zhangsan 刷新页面：侧边栏与按钮立即出现新增入口；调用相应接口不再返回 403。
4. 反向操作（取消勾选）同样立即生效，无需 zhangsan 重新登录。

## 说明与注意事项

- **权限实时加载的取舍**：为保证「改了立刻生效」，权限不做缓存，每次请求联表查询。若后续 QPS 升高，可引入短 TTL（如 10 秒）的 Redis 权限缓存，用少量生效延迟换取数据库压力下降。
- **超级管理员**：`admin` 角色的权限同样存在 `sys_role_menu` 中，可动态调整。请谨慎取消其角色管理相关权限，以免无法再次授权。内置 `admin` 角色与 `admin` 用户不允许删除。
- **单会话模型**：一个用户同时只有一个有效会话，后登录会顶掉先前的会话。
