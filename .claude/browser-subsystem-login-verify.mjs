#!/usr/bin/env node
/**
 * 专项验证：**子系统直连授权服务**（真 Chrome，headless，CDP 驱动）
 *
 * 登录链路上没有门户 —— 门户是「一键登录所有子系统」的独立入口，不是登录页。
 *
 *   流程 1  未登录打开营销系统 → 落在授权服务的登录页（5174/login）→ 登录 → 回到营销系统
 *   流程 2  同一浏览器再打开订单中心 → **全程不出现登录页**（凭授权服务会话静默发码）
 *   流程 3  走这条链路拿到的令牌是**各子系统自己的**（client_id 不同、scope=read），
 *           且子系统的权限判定照旧（admin 能写；zhangsan 只读、写被拒）
 *
 * 断言尽量落在「导航链 + 令牌声明」上，而不是只看终点。
 */
import {
  AUTH_WEB, MARKETING_WEB, ORDER_WEB, PORT,
  check, checkTrue, summary, launchChrome, firstTab, newTab,
  submitOauthLogin, tokenClaims
} from './cdp.mjs'

const LANDING = `${AUTH_WEB}/login`
/** 第二个用户要另起一个浏览器实例（同实例的标签页共享 Cookie 与 localStorage） */
const PORT_2 = PORT + 1

const settled = (origin, tokenKey) =>
  `location.origin === ${JSON.stringify(origin)}`
  + ` && !!localStorage.getItem(${JSON.stringify(tokenKey)})`
  + ` && !location.pathname.startsWith("/oauth")`
  + ` && !location.pathname.startsWith("/login")`

async function main() {
  const b1 = await launchChrome()
  console.log('浏览器 1 已启动（port=%s）', b1.port)
  let b2 = null

  try {
    // ================================================================ 流程 1
    console.log()
    console.log('='.repeat(90))
    console.log('流程 1：未登录打开营销系统 —— 直接落在授权服务的登录页')
    console.log('='.repeat(90))
    const tab1 = await firstTab(b1.port)
    await tab1.goto(MARKETING_WEB + '/')
    await tab1.waitFor(`location.href.includes("${LANDING}")`, '落到登录页', 30000)
    checkTrue('落点是授权服务的登录页', true, await tab1.url())

    const trail = tab1.documents.map((u) => u.replace(/^https?:\/\/[^/]+/, ''))
    console.log('  导航链：%s', trail.join('  →  '))
    check('链路上没有出现门户（5177）',
      tab1.documents.some((u) => u.includes('5177')), false)
    check('链路经过了本系统的授权端点',
      tab1.documents.some((u) => u.includes('client_id=marketing')), true)

    // 「前端不暴露 client_id / client_secret」的浏览器侧证据：
    // 前端从不请求「把授权地址返回给我」的接口，它导航的是后端的 302 端点
    check('前端没有请求 /api/auth/authorize-url（该接口已下线）',
      tab1.requests.filter((u) => u.includes('/api/auth/authorize-url')).length, 0)
    checkTrue('前端导航到的是后端的 302 端点（地址与密钥只在服务端拼）',
      tab1.requests.some((u) => u.includes('/api/auth/sso-login')), true)

    await submitOauthLogin(tab1, 'admin', 'admin123')
    await tab1.waitFor(settled(MARKETING_WEB, 'marketing_access_token'), '回到营销系统', 40000)
    checkTrue('登录后回到营销系统', true, await tab1.url())
    check('登录页全程只出现一次',
      tab1.documents.filter((u) => u.includes(LANDING)).length, 1)
    check('账号密码全程只提交一次',
      tab1.documents.filter((u) => u.includes('oauth/login')).length, 1)

    const mkClaims = await tokenClaims(tab1, 'marketing_access_token')
    check('营销系统的令牌是发给「营销系统」这个客户端的', mkClaims && mkClaims.client_id,
      'marketing')
    check('令牌范围是 read', mkClaims && mkClaims.scope, ['read'])
    check('令牌身份是 admin', mkClaims && mkClaims.user_name, 'admin')

    // ================================================================ 流程 2
    console.log()
    console.log('='.repeat(90))
    console.log('流程 2：同一浏览器再打开订单中心 —— 全程不应出现登录页')
    console.log('='.repeat(90))
    const tab2 = await newTab(tab1, ORDER_WEB + '/')
    await tab2.waitFor(settled(ORDER_WEB, 'order_access_token'), '落在订单中心', 40000)
    checkTrue('直接落在订单中心', true, await tab2.url())
    check('这一趟没有出现登录页',
      tab2.documents.filter((u) => u.includes(LANDING)).length, 0)
    check('这一趟没有提交账号密码',
      tab2.documents.filter((u) => u.includes('oauth/login')).length, 0)
    check('凭授权服务的会话静默发码（走到了订单中心自己的回调）',
      tab2.documents.some((u) => u.includes('5176/oauth/callback?code=')), true)

    const odClaims = await tokenClaims(tab2, 'order_access_token')
    check('订单中心的令牌是发给「订单中心」这个客户端的', odClaims && odClaims.client_id,
      'order-center')
    checkTrue('两个子系统拿到的是各自不同的令牌',
      mkClaims && odClaims && mkClaims.jti !== odClaims.jti,
      `${mkClaims && mkClaims.jti} vs ${odClaims && odClaims.jti}`)

    // ================================================================ 流程 3
    console.log()
    console.log('='.repeat(90))
    console.log('流程 3：走这条链路进入后，子系统那边的权限判定照旧')
    console.log('='.repeat(90))
    const adminProbe = await tab2.eval(`(async () => {
      const h = { 'Content-Type': 'application/json',
        Authorization: 'Bearer ' + localStorage.getItem('order_access_token') };
      const created = await (await fetch('/api/order', { method: 'POST', headers: h,
        body: JSON.stringify({ orderNo: 'DIRECT-ENTRY-ADMIN', customerName: '直连入口-admin',
          orderAmount: 1.23, status: 1, orderTime: '2026-10-17 09:00:00' }) })).json();
      const page = await (await fetch(
        '/api/order/page?pageNum=1&pageSize=5&orderNo=DIRECT-ENTRY-ADMIN', { headers: h })).json();
      const id = page.data && page.data.records[0] && page.data.records[0].id;
      let del = null;
      if (id) del = (await (await fetch('/api/order/' + id, { method: 'DELETE', headers: h })).json()).code;
      return 'create=' + created.code + ' deleted=' + del;
    })()`)
    check('admin 照样有写权限', adminProbe, 'create=200 deleted=200')

    // zhangsan 必须换一个独立浏览器实例
    b2 = await launchChrome(PORT_2)
    console.log('浏览器 2 已启动（port=%s）', b2.port)
    const zsTab = await firstTab(b2.port)
    await zsTab.goto(ORDER_WEB + '/')
    await zsTab.waitFor(`location.href.includes("${LANDING}")`, 'zhangsan 落到登录页', 30000)
    checkTrue('另一个浏览器里同样落在授权服务的登录页', true, await zsTab.url())
    await submitOauthLogin(zsTab, 'zhangsan', 'admin123')
    await zsTab.waitFor(settled(ORDER_WEB, 'order_access_token'), 'zhangsan 落在订单中心', 40000)

    const zs = JSON.parse(await zsTab.eval(`(async () => {
      const h = { 'Content-Type': 'application/json',
        Authorization: 'Bearer ' + localStorage.getItem('order_access_token') };
      const info = await (await fetch('/api/auth/info', { headers: h })).json();
      const page = await (await fetch('/api/order/page?pageNum=1&pageSize=5', { headers: h })).json();
      const stat = await (await fetch('/api/order/stat', { headers: h })).json();
      const write = await fetch('/api/order', { method: 'POST', headers: h,
        body: JSON.stringify({ orderNo: 'DIRECT-ENTRY-ZS', customerName: '直连入口-越权',
          orderAmount: 1 }) });
      return JSON.stringify({
        perms: (info.data && info.data.permissions || []).sort(),
        read: page.code, stat: stat.code, write: write.status
      });
    })()`))
    console.log('  zhangsan 的权限点：%s', zs.perms.join(', '))
    check('zhangsan 只有订单的读权限', zs.perms, ['order:order:list', 'order:stat:list'])
    check('读接口照常可用（列表 + 统计）', [zs.read, zs.stat], [200, 200])
    check('写接口被拒（403）—— 换了登录入口也没松权限', zs.write, 403)
    checkTrue('写入被拒时没有留下脏数据',
      await zsTab.eval(`(async () => {
        const r = await fetch('/api/order/page?pageNum=1&pageSize=20&orderNo=DIRECT-ENTRY-ZS',
          { headers: { Authorization: 'Bearer ' + localStorage.getItem('order_access_token') } });
        const b = await r.json();
        return (b.data.records || []).length === 0;
      })()`), true)
  } finally {
    b1.child.kill()
    if (b2) b2.child.kill()
  }
  process.exit(summary() ? 1 : 0)
}

main().catch((e) => {
  console.error('\n执行失败：', e.message)
  process.exit(2)
})
