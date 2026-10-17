import { getAccessToken, clearTokens } from '@/utils/auth'

/**
 * 统一登出（Single Logout）—— 必须做两件事，缺一件都会「登出得不干净」
 * <p>
 * 本系统自己删不掉令牌：令牌由 rbac-oauth 签发、自包含，服务端手里没有它的记录。
 * 真正让令牌失效的动作在授权服务那边，而那里有两个**独立的**登录态要清：
 *
 * <ol>
 *   <li><b>SSO 会话</b>（{@code sso:session:{username}}）—— 删掉它，本系统与所有其他子系统
 *       手里的旧令牌下一个请求就失效（各子系统每个请求都拿令牌去 {@code /oauth/check_token}，
 *       而那边会要求这个会话存在）。</li>
 *   <li><b>授权服务的登录态</b>（Redis {@code oauth2:auth:{token}} + 浏览器 Cookie {@code RBAC_OAUTH2_AUTH}）
 *       —— 授权服务靠它记住「这个人已经认证过了」。不清掉的话，浏览器能静默重走一遍授权码、换出新令牌，
 *       而签发新令牌会**重建** SSO 会话 —— 等于登出被撤销了。</li>
 * </ol>
 *
 * <h3>为什么不能只做一件事</h3>
 * 这两件事各自的「认人」方式不同，而**前端只有一种方式能同时满足**：
 * <table border="1">
 *   <caption>两种登出请求能认出谁</caption>
 *   <tr><th>怎么发请求</th><th>能带 Authorization 头</th><th>能带 Cookie</th></tr>
 *   <tr><td>fetch（XHR）</td><td>能</td><td>能（{@code credentials: 'include'}）</td></tr>
 *   <tr><td>整页跳转</td><td><b>不能</b></td><td>能</td></tr>
 * </table>
 * 于是：
 * <ul>
 *   <li>只做整页跳转 —— <b>密码模式登录的用户会被漏掉</b>。密码是子系统后端带客户端凭证去换的令牌，
 *       浏览器全程没参与，因此浏览器里<b>没有</b>授权服务的登录态；跳转请求既没头也没 Cookie，
 *       授权服务认不出「是谁在登出」，SSO 会话原封不动 —— 其他子系统照常能用。
 *       （这是实际踩到的坑：从营销系统登出后，订单中心仍然进得去。）</li>
 *   <li>只做 fetch —— 被踢下线的标签页手里已经没有有效令牌，fetch 带不出能认人的东西；
 *       只有整页跳转那份 Cookie 还能让授权服务认出「是谁」。少了这一步，Redis 里的登录态删不掉，
 *       浏览器回到授权页仍是登录状态。</li>
 * </ul>
 * 所以这里两步都做：先 fetch（带令牌，认得出密码模式的用户），再跳转（带 Cookie，删登录态并把用户带到登录页）。
 * 两步都是幂等的，任一步失败也不会把另一步带崩。
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
        // 带上 Cookie：浏览器里若还有授权服务的登录态，也一并在这步被处理掉
        credentials: 'include',
        headers: { Authorization: `Bearer ${token}` }
      })
    } catch (e) {
      // 授权服务不可达时不阻断登出：至少把本地凭证清掉，并继续走下面的跳转，
      // 让用户能离开当前页面（真正的服务端失效会在他下次访问时由 check_token 兜住）
      console.warn('统一登出时调用授权服务失败，继续走整页跳转', e)
    }
  }

  clearTokens()

  if (!oauthServer) {
    return false
  }
  // 第二步：整页跳转。浏览器带着 Cookie 请求，授权服务会删掉登录态并写入过期 Cookie，
  // 最后 302 回下面这个地址（redirect_uri 只接受已登记客户端的来源）。
  // 回跳到本系统的 /login：那里会顺着统一入口一路走到授权服务的登录页，
  // 于是「登出」与「未登录进入」落到同一页，而且带着待处理的授权请求 ——
  // 用户在那儿重新输入账号密码就能继续用，不会出现「输完了却登不进去」的死角。
  const back = `${window.location.origin}/login`
  window.location.href = `${oauthServer}/oauth/sso-logout?redirect_uri=${encodeURIComponent(back)}`
  return true
}
