-- ============================================================
-- 增量升级：OAuth2 客户端配置（第三方系统授权登录接入）
-- 适用于已按 schema.sql 初始化过的旧库；全新安装请把本文件内容合并进 schema.sql + data.sql
-- 注意：本脚本一次性执行，重复执行会因 CREATE TABLE 已存在而报错
--
-- 【执行后必做】清理 Redis 中的权限快照，否则已登录用户（含 admin）读到的仍是登录时的旧快照，
-- 看不到新菜单，必须重新登录才行。快照只是缓存，清掉不会把人踢下线，下一次请求会自动重建：
--   redis-cli EVAL "local ks=redis.call('keys','login:perm:*'); for i,k in ipairs(ks) do redis.call('del',k) end; return #ks" 0
-- ============================================================

USE `rbac`;

-- ---------- OAuth2 客户端配置表 ----------
-- 沿用 Spring Security OAuth 的 clientdetails 约定：
--   主键为 appId（字符串，非自增）；列名用驼峰，与项目其余表的蛇形命名不同
--   grantTypes / scope / redirectUrl / authorities / autoApproveScopes 均为逗号分隔多值
--   有效期单位为秒（access/refresh 各自独立）
--   appSecret 为 NOT NULL：客户端密钥不允许为空（空密钥等同于公开客户端），存 AES 密文
-- 说明：本表不加 status / del_flag / 时间戳，以保持与 OAuth2 客户端加载逻辑的字段一致。
CREATE TABLE `clientdetails` (
    `appId`                  VARCHAR(128)  NOT NULL COMMENT '客户端标识，唯一',
    `resourceIds`            VARCHAR(255)  DEFAULT NULL COMMENT '资源ID，逗号分隔',
    `appSecret`              VARCHAR(255)  NOT NULL COMMENT '客户端密钥（AES 密文，不允许为空）',
    `scope`                  VARCHAR(255)  DEFAULT NULL COMMENT '授权范围，逗号分隔',
    `grantTypes`             VARCHAR(255)  DEFAULT NULL COMMENT '授权模式，逗号分隔：authorization_code/client_credentials/password/refresh_token/implicit',
    `redirectUrl`            VARCHAR(255)  DEFAULT NULL COMMENT '回调地址，逗号分隔',
    `authorities`            VARCHAR(255)  DEFAULT NULL COMMENT '客户端本身拥有的权限，逗号分隔',
    `access_token_validity`  INT           DEFAULT NULL COMMENT '访问令牌有效期（秒）',
    `refresh_token_validity` INT           DEFAULT NULL COMMENT '刷新令牌有效期（秒）',
    `additionalInformation`  VARCHAR(4096) DEFAULT NULL COMMENT '扩展信息（JSON）',
    `autoApproveScopes`      VARCHAR(255)  DEFAULT NULL COMMENT '自动放行的授权范围，逗号分隔',
    PRIMARY KEY (`appId`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='OAuth2 客户端配置';

-- ---------- 菜单：客户端管理页面 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (107, 1, '客户端管理', 'client', 'system/client/index', 'system:client:list', 2, 'Key', 7, 1, 1, NOW(), NOW());

-- ---------- 按钮权限：客户端管理 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1071, 107, '客户端查询', NULL, NULL, 'system:client:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1072, 107, '客户端新增', NULL, NULL, 'system:client:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1073, 107, '客户端修改', NULL, NULL, 'system:client:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1074, 107, '客户端删除', NULL, NULL, 'system:client:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- ---------- admin 角色授予该菜单（否则 admin 也看不到）----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 107), (1, 1071), (1, 1072), (1, 1073), (1, 1074);

-- ---------- 示例 OAuth2 客户端（第三方系统接入，覆盖全部五种授权模式）----------
-- 授权服务器会读取本表：grantTypes 决定该客户端能用哪些模式，
-- authorization_code / password / implicit / client_credentials / refresh_token 均受支持。
-- appSecret 存的是明文 client123 经 AES 加密后的密文（密钥来自配置项 oauth2.client-secret-key）——
-- 用可逆加密而非哈希，是为了让「客户端管理」页面能展示明文供复制给第三方。
-- 下面这条密文是用默认配置密钥加密的；若你改过 oauth2.client-secret-key，请改为在页面上重新设置密钥。
-- redirectUrl 最后一项是本项目自带的授权码回调联调端点（GET /oauth/test-callback）；
-- 换用其他回调地址时必须一并登记，否则授权端点会以「回调地址不匹配」拒绝。
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`, `authorities`, `access_token_validity`, `refresh_token_validity`, `additionalInformation`, `autoApproveScopes`)
VALUES ('rbac-third-party', NULL, 'cnZXzMbXqybU6Pwk0x4XTbHWZOgE8o9GCrb4JKkrLIHxu84jyQ==',
        'read,write', 'authorization_code,password,implicit,client_credentials,refresh_token',
        'http://localhost:8080/callback,http://localhost:5173/oauth/callback,http://localhost:7070/oauth/test-callback',
        'ROLE_CLIENT', 3600, 604800, NULL, NULL);

-- 【注意】旧版本曾用 BCrypt 存 appSecret（单向哈希，`$2a$10$...`）。这种数据解密不了，
-- 授权服务器会明确提示「客户端密钥无法解密」。请到「客户端管理」页面重新设置一次密钥即可。
-- 查询：SELECT appId, appSecret FROM clientdetails;

-- ============================================================
-- 【增量】客户端密钥不允许为空
-- 上面的 CREATE TABLE 只在建表时生效。若 clientdetails 表已存在，需单独执行下面这段收紧约束。
-- 执行前先确认没有空密钥的客户端，否则 ALTER 会失败：
--   SELECT appId FROM clientdetails WHERE appSecret IS NULL OR TRIM(appSecret) = '';
-- 若有记录，请先在「客户端管理」页面为它设置密钥，再执行 ALTER。
-- ============================================================
ALTER TABLE `clientdetails`
    MODIFY `appSecret` VARCHAR(255) NOT NULL COMMENT '客户端密钥（AES 密文，不允许为空）';
