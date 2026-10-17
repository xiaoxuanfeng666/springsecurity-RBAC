#!/usr/bin/env node
/**
 * 跨浏览器验证：同一个账号在两个**独立浏览器**里登录，其中一个登出后会发生什么。
 *
 * 两个实例（不同端口、不同 profile）＝两份互不相干的 Cookie 与 localStorage，
 * 等价于「Chrome 登一个、Edge 登一个」。
 *
 * 想回答的问题：
 *   1. 统一登出是「按用户」还是「按浏览器」的？—— SSO 会话是 `sso:session:{username}` 一个键，
 *      所以一个浏览器登出，另一个浏览器的**令牌**也应该立刻失效。
 *   2. 反过来，第二个浏览器登录时会不会踢掉第一个？—— 会踢掉它的**登录态**（Cookie），
 *      但不会让它的令牌失效（SSO 会话还在）。
 */
import { execFileSync } from 'node:child_process'
import {
  ORDER_WEB, AUTH_WEB,
  check, checkTrue, summary, sleep, launchChrome, firstTab,
  submitOauthLogin
} from './cdp.mjs'

const LANDING = `${AUTH_WEB}/login`
const PORT_2 = 9334

/**
 * 用给定的令牌直接打订单接口，返回 HTTP 状态。
 * 走 Node 的 fetch 而不是页面里的 fetch：登出后标签页已经跳到 5174，
 * 那里既没有订单中心的 localStorage，也没有 /api 代理 —— 在页面里测会测错东西。
 */
async function probeStat(token) {
  const r = await fetch('http://localhost:7073/order/stat', {
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  })
  return r.status
}

/** 这条登录态在 Redis 里还在不在（0 = 已删除） */
function authStateExists(token) {
  const out = execFileSync('redis-cli', ['--raw', 'exists', 'oauth2:auth:' + token],
    { encoding: 'utf-8' }).trim()
  return out === '1'
}

/** 取某个浏览器在授权服务域上的登录态 Cookie 值 */
async function authCookieOf(tab) {
  const r = await tab.send('Network.getCookies', { urls: [AUTH_WEB] })
  const hit = (r.cookies || []).find((c) => c.name === 'RBAC_OAUTH2_AUTH')
  return hit ? hit.value : null
}

async function loginIn(tab) {
  await tab.goto(ORDER_WEB + '/')
  await tab.waitFor(`location.href.includes("${LANDING}")`, '落到登录页', 30000)
  await submitOauthLogin(tab, 'admin', 'admin123')
  await tab.waitFor(`!!localStorage.getItem('order_access_token')`, '拿到令牌', 40000)
  return tab.eval('localStorage.getItem("order_access_token")')
}

async function main() {
  const browserA = await launchChrome()
  const browserB = await launchChrome(PORT_2)
  console.log('浏览器 A（port=%s）、浏览器 B（port=%s）已启动', browserA.port, browserB.port)

  try {
    // ============================================================ 准备
    console.log()
    console.log('='.repeat(90))
    console.log('准备：两个独立浏览器分别用 admin 登录订单中心')
    console.log('='.repeat(90))
    const tabA = await firstTab(browserA.port)
    const tokenA = await loginIn(tabA)
    check('浏览器 A 的订单接口可用', await probeStat(tokenA), 200)

    const tabB = await firstTab(browserB.port)
    const tokenB = await loginIn(tabB)
    check('浏览器 B 的订单接口可用', await probeStat(tokenB), 200)
    checkTrue('两个浏览器拿到的是各自的令牌', tokenA !== tokenB,
      `${tokenA.slice(0, 12)}… vs ${tokenB.slice(0, 12)}…`)

    const cookieA = await authCookieOf(tabA)
    const cookieB = await authCookieOf(tabB)
    checkTrue('两个浏览器各有一份独立的登录态 Cookie', cookieA && cookieB && cookieA !== cookieB,
      `${(cookieA || '').slice(0, 8)}… vs ${(cookieB || '').slice(0, 8)}…`)

    // ============================================================ 现状 1
    console.log()
    console.log('='.repeat(90))
    console.log('现状 1：B 登录之后，A 手里那个令牌还能用吗？')
    console.log('='.repeat(90))
    check('A 的旧令牌仍然可用（SSO 会话是按用户的一个键，B 登录只是续期它）',
      await probeStat(tokenA), 200)

    // ============================================================ 核心
    console.log()
    console.log('='.repeat(90))
    console.log('核心：在浏览器 B 点「退出登录」—— A 的令牌会失效吗？')
    console.log('='.repeat(90))
    await tabB.bringToFront()
    await sleep(300)
    await tabB.eval(`(() => {
      const el = [...document.querySelectorAll('button, .el-dropdown-menu__item, li, span, a')]
        .find((e) => e.innerText.trim() === '退出登录');
      if (!el) return false; el.click(); return true;
    })()`)
    await tabB.waitFor(
      'document.querySelector(".el-message-box__btns .el-button--primary") !== null', '确认框', 5000)
    await tabB.eval('document.querySelector(".el-message-box__btns .el-button--primary").click()')
    await tabB.waitFor(`location.href.includes("${LANDING}")`, 'B 落到登录页', 30000)
    checkTrue('浏览器 B 登出完成，停在授权服务的登录页', true, await tabB.url())

    check('浏览器 A 的令牌**在 B 登出后立刻失效**（跨浏览器统一登出生效）',
      await probeStat(tokenA), 401)
    check('浏览器 B 自己的令牌也失效', await probeStat(tokenB), 401)

    // 另一个浏览器的 Cookie 技术上没法替它删掉，但它指向的登录态必须已经不在了
    checkTrue('A 浏览器里那份 Cookie 已经指向不存在的登录态（无法再静默登录）',
      !authStateExists(cookieA), `EXISTS oauth2:auth:${(cookieA || '').slice(0, 8)}… -> 0`)
    check('B 的登录态也已从 Redis 删除', authStateExists(cookieB), false)

    // ============================================================ 现状 2
    console.log()
    console.log('='.repeat(90))
    console.log('现状 2：A 此时再请求一次接口，前端会怎么处理')
    console.log('='.repeat(90))
    // 让 A 的 401 拦截器跑起来：触发一次正常的业务请求（不是直接 fetch）
    await tabA.bringToFront()
    await tabA.eval('location.reload()')
    await sleep(4000)
    const urlA = await tabA.url()
    console.log('  A 刷新后的地址：%s', urlA)
    checkTrue('A 被带到登录页（登录态已没了，无法静默重登）', urlA.includes(LANDING), urlA)
    check('A 的本地令牌已被前端清掉',
      await tabA.eval('localStorage.getItem("order_access_token")'), null)
  } finally {
    browserA.child.kill()
    browserB.child.kill()
  }
  process.exit(summary() ? 1 : 0)
}

main().catch((e) => {
  console.error('\n执行失败：', e.message)
  process.exit(2)
})
