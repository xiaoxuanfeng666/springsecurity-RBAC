#!/usr/bin/env node
/**
 * 门户的「一键登录所有子系统」端到端（真 Chrome，headless，CDP 驱动）
 *
 * 门户现在是**独立入口**（不再参与子系统的登录链）：用户直接访问它，点「统一登录」，
 * 再在工作台点一次「一键登录所有子系统」，其余子系统应当在隐藏 iframe 里静默拿到令牌，
 * 全程不出现登录页、不出现授权确认页。
 *
 *   流程 1  登录门户（已登录过则凭会话直接进）
 *   流程 2  一键登录所有子系统：卡片全部变成「已签发令牌」，另开标签页访问子系统免登录
 *   流程 3  统一登出：门户与被踢的子系统标签页都落到授权服务的登录页
 */
import {
  ORDER_WEB, MARKETING_WEB, PORTAL_WEB, AUTH_WEB,
  check, checkTrue, summary, sleep, launchChrome, firstTab, newTab,
  submitOauthLogin, OK_TAGS, NO_TAGS
} from './cdp.mjs'

const LANDING = `${AUTH_WEB}/login`

async function clickText(tab, text) {
  return tab.eval(`(() => {
    const el = [...document.querySelectorAll('button, .el-dropdown-menu__item, li, span, a')]
      .find((e) => e.innerText.trim() === ${JSON.stringify(text)});
    if (!el) return false; el.click(); return true;
  })()`)
}

async function confirmBox(tab) {
  try {
    await tab.waitFor(
      'document.querySelector(".el-message-box__btns .el-button--primary") !== null', '确认框', 5000)
    await tab.eval('document.querySelector(".el-message-box__btns .el-button--primary").click()')
    return true
  } catch (e) {
    return false
  }
}

async function main() {
  const { child, profile } = await launchChrome()
  console.log('Chrome 已启动（%s）', profile)

  try {
    // ---------------------------------------------------------------- 流程 1
    console.log()
    console.log('='.repeat(90))
    console.log('流程 1：登录门户（未登录时会落在授权服务的登录页）')
    console.log('='.repeat(90))
    const portalTab = await firstTab()
    await portalTab.goto(PORTAL_WEB + '/')
    // 未登录直接访问门户：门户自己的入口页（一个「统一登录」按钮），
    // 点它之后才去授权服务的登录页 —— 门户是独立入口，不参与子系统的登录链。
    await portalTab.waitFor(
      'document.body.innerText.includes("我的子系统")'
      + ' || document.body.innerText.includes("统一登录")',
      '门户打开', 25000)

    if (!(await portalTab.eval('document.body.innerText.includes("我的子系统")'))) {
      checkTrue('未登录访问门户：门户给出自己的登录入口', true, await portalTab.url())
      await portalTab.eval(`(() => {
        const b = [...document.querySelectorAll('button')].find(x => x.innerText.includes('统一登录'));
        if (!b) return false; b.click(); return true;
      })()`)
      await portalTab.waitFor(`location.href.includes("${LANDING}")`, '门户转到登录页', 30000)
      checkTrue('从门户进入登录：落到授权服务的登录页', true, await portalTab.url())
      await submitOauthLogin(portalTab, 'admin', 'admin123')
      await portalTab.waitFor(`!!localStorage.getItem('portal_access_token')`, '门户拿到令牌', 40000)
    }
    await portalTab.waitFor('document.body.innerText.includes("我的子系统")',
      '门户工作台渲染', 30000)
    checkTrue('进入门户工作台', true, await portalTab.url())

    // ---------------------------------------------------------------- 流程 2
    console.log()
    console.log('='.repeat(90))
    console.log('流程 2：一键登录所有子系统（隐藏 iframe 静默换令牌）')
    console.log('='.repeat(90))
    const clients = () => portalTab.eval(`(async () => {
      const r = await fetch('/api/portal/info', {
        headers: { Authorization: 'Bearer ' + localStorage.getItem('portal_access_token') } });
      const b = await r.json();
      return (b.data && b.data.clients || []).slice().sort().join(',');
    })()`)

    const before = await clients()
    const okBefore = await portalTab.eval(OK_TAGS)
    console.log('  点击前：已签发令牌 %s 个；服务端 clients=%s', okBefore, before)
    check('点击前只有一个客户端有令牌（只有门户自己）', before, 'portal')

    await portalTab.eval(`(() => {
      const btn = [...document.querySelectorAll('button')].find(b => b.innerText.includes('一键登录'));
      if (!btn) return false; btn.click(); return true;
    })()`)
    await portalTab.waitFor(OK_TAGS + ' === 2', '两个子系统都变成已签发令牌', 45000)
    // el-tag 自带 el-zoom-in-center 过渡：旧标签在离场动画的约 300ms 内还留在 DOM 里，等它落定再数
    await portalTab.waitFor(NO_TAGS + ' === 0', '旧「未登录」标签的离场动画结束', 5000)
    check('一键登录后：两个子系统都已签发令牌', await portalTab.eval(OK_TAGS), 2)
    check('一键登录后：没有「未登录」的卡片', await portalTab.eval(NO_TAGS), 0)
    check('服务端 SSO 会话里记着三个客户端', await clients(), 'marketing,order-center,portal')

    // 直接验结果：另开标签页访问营销系统，应该免登录直接进
    const mkTab = await newTab(portalTab, MARKETING_WEB + '/')
    await mkTab.waitFor(`!!localStorage.getItem('marketing_access_token')`, '营销系统拿到令牌', 20000)
    const mkUrl = await mkTab.url()
    checkTrue('另开标签页访问营销系统：免登录直接进（说明 iframe 真的把令牌送达了）',
      mkUrl.startsWith(MARKETING_WEB) && !mkUrl.includes('/login'), mkUrl)
    check('营销系统的令牌是发给「营销系统」这个客户端的',
      (await mkTab.eval(`(() => {
        const t = localStorage.getItem('marketing_access_token');
        return t ? JSON.parse(atob(t.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'))).client_id : null;
      })()`)), 'marketing')

    console.log('  截图：%s', await portalTab.screenshot('oneclick-1-workbench.png'))

    // ---------------------------------------------------------------- 流程 3
    console.log()
    console.log('='.repeat(90))
    console.log('流程 3：统一登出 —— 门户与被踢的子系统都落到授权服务的登录页')
    console.log('='.repeat(90))
    const odTab = await newTab(portalTab, ORDER_WEB + '/')
    await odTab.waitFor(`!!localStorage.getItem('order_access_token')`, '订单中心标签页就绪', 25000)

    await portalTab.bringToFront()
    await sleep(300)
    check('点到「统一登出」', await clickText(portalTab, '统一登出'), true)
    await confirmBox(portalTab)
    await portalTab.waitFor(`location.href.includes("${LANDING}")`, '门户落到登录页', 30000)
    checkTrue('门户标签页落在授权服务的登录页', true, await portalTab.url())

    // 切回订单中心标签页触发惰性校验（节流窗口 1 秒，等它过去）
    await odTab.bringToFront()
    await sleep(1500)
    let trace = []
    const deadline = Date.now() + 20000
    while (Date.now() < deadline) {
      const u = await odTab.url()
      if (trace[trace.length - 1] !== u) trace.push(u)
      if (u.includes(LANDING)) break
      await sleep(300)
    }
    console.log('  订单中心标签页的轨迹：%s', trace.join('  →  '))
    checkTrue('被踢的子系统标签页也落在授权服务的登录页',
      (await odTab.url()).includes(LANDING), await odTab.url())
    check('订单中心的本地令牌也清掉了',
      await odTab.eval('localStorage.getItem("order_access_token")'), null)
  } finally {
    child.kill()
  }
  process.exit(summary() ? 1 : 0)
}

main().catch((e) => {
  console.error('\n执行失败：', e.message)
  process.exit(2)
})
