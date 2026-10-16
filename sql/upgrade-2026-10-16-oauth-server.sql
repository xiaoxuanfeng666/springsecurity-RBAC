-- 增量升级：OAuth2 授权服务器拆分为独立服务（rbac-oauth，端口 7071）
--
-- 背景：授权服务器（/oauth/**）、客户端管理接口（/client/**）与第三方开放接口（/openapi/**）
-- 已从 rbac-backend 拆到新服务 rbac-oauth，两个服务共用同一个库。
-- 表结构没有变化（clientdetails 仍由新服务读写，sys_user/sys_role/sys_menu 也不变），
-- 唯一需要调整的是示例客户端里登记过的授权码回调地址：
-- 授权码回调联调端点 GET /oauth/test-callback 随授权服务器一起搬到了 7071，
-- 旧的 7070 地址在新服务上并不存在，授权端点会以「回调地址不匹配」直接拒绝。
--
-- 若你自行登记过其他指向 7070 的回调地址，也需要一并改到授权服务的地址。
-- 授权服务地址与授权服务器的对外地址一致；生产环境请按实际部署地址调整（或用「客户端管理」页面改）。
--
-- 全新安装不需要执行本脚本：schema.sql / data.sql 里已经是新地址。

UPDATE `clientdetails`
SET `redirectUrl` = REPLACE(`redirectUrl`, 'localhost:7070', 'localhost:7071')
WHERE `redirectUrl` LIKE '%localhost:7070%';

-- 查询确认：SELECT appId, redirectUrl FROM clientdetails;
