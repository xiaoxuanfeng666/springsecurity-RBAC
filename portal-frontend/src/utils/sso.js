import { getAccessToken, clearTokens } from '@/utils/auth'

/**
 * 统一登出（Single Logout）—— 门户是这件事最合适的入口
 * <p>
 * 在这里登出，等于把整张访问凭据网一次撤掉。授权服务那边有两个**独立的**登录态要清：
 *
 * <ol>
 *   <li><b>SSO 会话</b>（{@code sso:session:{username}}）—— 删掉它，门户与所有子系统手里的
 *       旧令牌下一个请求就失效（各子系统每个请求都拿令牌去 {@code /oauth/check_token}，
 *       而那边会要求这个会话存在）。</li>
 *   <li><b>授权服务的登录态</b>（Redis {@code oauth2:auth:{token}} + Cookie {@code RBAC_OAUTH2_AUTH}）—— 授权服务靠它记住
 *       「这个人已经认证过了」。不清掉的话，浏览器能静默重走一遍授权码、换出新令牌，
 *       而签发新令牌会**重建** SSO 会话 —— 等于登出被撤销了。</li>
 * </ol>
 *
 * <h3>为什么必须 fetch + 跳转两步</h3>
 * 这两件事的「认人」方式不同，而前端只有一种方式能同时满足：
 * <table border="1">
 *   <caption>两种登出请求能认出谁</caption>
 *   <tr><th>怎么发请求</th><th>能带 Authorization 头</th><th>能带 Cookie</th></tr>
 *   <tr><td>fetch（XHR）</td><td>能</td><td>能（{@code credentials: 'include'}）</td></tr>
 *   <tr><td>整页跳转</td><td><b>不能</b></td><td>能</td></tr>
 * </table>
 * <ul>
 *   <li>只跳转 —— 登录态那 30 分钟一到期就认不出人了（门户令牌却是 1 小时），
 *       这种情况下 SSO 会话根本没被删，子系统照常能用。同理，用密码模式登录子系统的人
 *       浏览器里从来没有过登录态，纯跳转对他们是完全无效的。</li>
 *   <li>只 fetch —— 被踢下线的标签页手里已经没有有效令牌，fetch 带不出能认人的东西；
 *       只有整页跳转那份 Cookie 还能让授权服务认出「是谁」。少了这一步，Redis 里的登录态删不掉，
 *       浏览器回到授权页仍是登录状态。</li>
 * </ul>
 * 所以两步都做：先 fetch（带令牌，一定认得出人），再跳转（带 Cookie，删登录态并把用户带到登录页）。
 * 两步都幂等，任一步失败也不会把另一步带崩。
 *
 * @returns {Promise<boolean>} 是否已发起整页跳转；false 表示未配置授权服务地址，调用方走本地登出兜底
 */
export async function ssoLogout() {
  const oauthServer = import.meta.env.VITE_OAUTH_SERVER_URL
  // 必须在清本地凭证之前读出来 —— 第一步就靠它认人
  const token = getAccessToken()

  if (oauthServer && token) {
    try {
      await fetch(`${oauthServer}/oauth/sso-logout`, {
        method: 'POST',
        credentials: 'include',
        headers: { Authorization: `Bearer ${token}` }
      })
    } catch (e) {
      console.warn('统一登出时调用授权服务失败，继续走整页跳转', e)
    }
  }

  clearTokens()

  if (!oauthServer) {
    return false
  }
  // 回跳门户自己的 /login，并带上「来处 = 门户自己」：门户登录页见带 redirect 会自动发起授权，
  // 于是浏览器落到**授权服务的登录页** —— 与「子系统未登录」「子系统登出」同一个落点。
  // （登录成功后 /portal/subsystems 里没有哪个子系统匹配门户自己的源，consumeReturnTarget
  //   会返回 null，自然回门户工作台。）
  const self = window.location.origin
  const back = `${self}/login?redirect=${encodeURIComponent(self)}`
  window.location.href = `${oauthServer}/oauth/sso-logout?redirect_uri=${encodeURIComponent(back)}`
  return true
}
