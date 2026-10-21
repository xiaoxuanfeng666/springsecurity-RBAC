/**
 * 统一登出（Single Logout）—— 第二步：清掉授权服务的浏览器登录态
 * <p>
 * 第一步已经由 {@code userStore.logout()} 完成：后台用服务端保管的 access token 请求
 * 授权服务销毁 SSO 会话（该用户所有系统立即失效），并删除本系统的 BFF 会话与 Cookie。
 * <p>
 * 但授权服务自己还有一个**浏览器 Cookie**（{@code RBAC_OAUTH2_AUTH}）记录着「这个人已认证」。
 * 后端到后端的请求带不上它，不清掉的话浏览器还能静默重走一遍授权码、换出新令牌并重建 SSO 会话
 * —— 等于登出被撤销了。这一步只能让浏览器自己发请求，所以必须整页跳转。
 *
 * @returns {Promise<boolean>} 是否已发起整页跳转；false 表示未配置授权服务地址，调用方走本地登出兜底
 */
export async function ssoLogout() {
  const oauthServer = import.meta.env.VITE_OAUTH_SERVER_URL
  if (!oauthServer) {
    return false
  }
  // 浏览器带着 Cookie 请求，授权服务删除登录态并写入过期 Cookie，
  // 最后 302 回下面这个地址（redirect_uri 只接受已登记客户端的来源）。
  // 回到本系统的 /login：那里会顺着统一入口走到授权服务的登录页，
  // 用户在那儿重新登录即可继续用，不会出现「输完了却登不进去」的死角。
  const back = `${window.location.origin}/login`
  window.location.href = `${oauthServer}/oauth/sso-logout?redirect_uri=${encodeURIComponent(back)}`
  return true
}
