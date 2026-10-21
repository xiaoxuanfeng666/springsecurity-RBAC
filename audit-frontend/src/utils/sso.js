import { logout as logoutApi } from '@/api/auth'

/**
 * 统一登出（Single Logout）—— 两步，缺一步都会「登出得不干净」
 * <p>
 * 浏览器手里没有任何令牌（BFF 模式），所以两件事的分工是：
 * <ol>
 *   <li><b>服务端会话</b>：调本系统 {@code POST /auth/logout}，后端用它保管的 access token
 *       请求授权服务销毁 SSO 会话（该用户所有子系统的令牌立刻失效），随后删掉 BFF 会话。
 *       令牌不在浏览器里，这一步只能由后端代劳。</li>
 *   <li><b>授权服务的浏览器登录态</b>（Cookie {@code RBAC_OAUTH2_AUTH}）：只能由浏览器自己清。
 *       后端到后端的请求带不上这个 Cookie；不清掉的话，浏览器还能静默重走一遍授权码，
 *       换出新令牌并<b>重建</b> SSO 会话 —— 等于登出被撤销了。</li>
 * </ol>
 * 所以先调接口，再整页跳转。两步都是幂等的，任一步失败也不会把另一步带崩。
 *
 * @returns {Promise<boolean>} 是否已发起整页跳转；false 表示未配置授权服务地址，调用方走本地登出兜底
 */
export async function ssoLogout() {
  const oauthServer = import.meta.env.VITE_OAUTH_SERVER_URL

  try {
    await logoutApi()
  } catch (e) {
    // 授权服务不可达时不阻断登出：本地会话与 Cookie 已由后端清理，
    // 继续走下面的跳转，让用户能离开当前页面
    console.warn('统一登出时调用本系统登出接口失败，继续走整页跳转', e)
  }

  if (!oauthServer) {
    return false
  }
  // 第二步：整页跳转。浏览器带着 Cookie 请求，授权服务会删掉登录态并写入过期 Cookie，
  // 最后 302 回下面这个地址（redirect_uri 只接受已登记客户端的来源）。
  // 回跳到本系统的 /login：那里会顺着统一入口一路走到授权服务的登录页。
  const back = `${window.location.origin}/login`
  window.location.href = `${oauthServer}/oauth/sso-logout?redirect_uri=${encodeURIComponent(back)}`
  return true
}
