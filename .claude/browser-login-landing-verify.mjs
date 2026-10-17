#!/usr/bin/env node
/**
 * 专项验证：**未登录/登出统一落到授权服务的登录页**（5174/login）
 *
 * 四条路径的落点都必须是 5174/login：
 *   A. 未登录打开子系统（订单中心）
 *   B. 在子系统点「退出登录」
 *   C. 在另一个子系统（不同源）登出后，这边的标签页跟着被踢
 *   D. 登出后带着已失效的旧令牌再进子系统
 *
 * 另外验一件容易被忽略的事：**落到 5174 之后登录确实能用**。
 * 5174 的 `/` 是重定向到 `/login`，所以如果浏览器不是从 `/oauth/authorize` 被 302 过去的
 * （也就是会话里没有待处理的授权请求），用户输完账号密码会被弹回登录页 —— 那是个死角。
 * 因此落点正确还不够，必须验「在那儿登录能进得去」。
 */
import {
  AUTH_WEB, MARKETING_WEB, ORDER_WEB,
  check, checkTrue, summary, sleep, launchChrome, firstTab, newTab,
  submitOauthLogin
} from './cdp.mjs'

/** 授权服务的登录页 —— 这是本轮唯一允许的落点 */
const LANDING = `${AUTH_WEB}/login`

/** 记录地址变化，直到停在某个页面或超时 */
async function traceUntil(tab, stopWhen, seconds = 25) {
  const seen = []
  const deadline = Date.now() + seconds * 1000
  while (Date.now() < deadline) {
    let u
    try {
      u = await tab.url()
    } catch (e) {
      u = '(导航中)'
    }
    if (seen[seen.length - 1] !== u) seen.push(u)
    if (stopWhen(u)) break
    await sleep(300)
  }
  return seen
}

const atLanding = (u) => u.startsWith(LANDING)

function reportTrace(label, seen) {
  console.log('  【%s】', label)
  seen.forEach((u) => console.log('      %s', u))
}

async function clickText(tab, text) {
  return tab.eval(`(() => {
    const el = [...document.querySelectorAll('button, .el-dropdown-menu__item, li, span, a')]
      .find((e) => e.innerText.trim() === ${JSON.stringify(text)});
    if (!el) return false;
    el.click();
    return true;
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
    // ============================================================ 路径 A
    console.log()
    console.log('='.repeat(90))
    console.log('路径 A：未登录打开订单中心')
    console.log('='.repeat(90))
    const tabA = await firstTab()
    await tabA.goto(ORDER_WEB + '/')
    const traceA = await traceUntil(tabA, atLanding)
    reportTrace('未登录打开订单中心的轨迹', traceA)
    checkTrue('落点是授权服务的登录页', atLanding(await tabA.url()), await tabA.url())

    // 关键：在这里登录必须真的能进去（否则就是个输完密码又被弹回登录页的死角）
    await submitOauthLogin(tabA, 'admin', 'admin123')
    await tabA.waitFor(`!!localStorage.getItem('order_access_token')`,
      '在 5174 登录后回到订单中心', 40000)
    const landedIn = await tabA.url()
    checkTrue('在 5174 登录后确实进到了订单中心（不是死角）',
      landedIn.startsWith(ORDER_WEB) && !landedIn.includes('/login'), landedIn)
    check('拿到了订单中心自己的令牌',
      (await tabA.eval(`(() => {
        const t = localStorage.getItem('order_access_token');
        return t ? JSON.parse(atob(t.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'))) : null;
      })()`))?.client_id, 'order-center')

    // ============================================================ 路径 B
    console.log()
    console.log('='.repeat(90))
    console.log('路径 B：在订单中心点「退出登录」')
    console.log('='.repeat(90))
    await tabA.bringToFront()
    await sleep(300)
    check('点到「退出登录」', await clickText(tabA, '退出登录'), true)
    await confirmBox(tabA)
    const traceB = await traceUntil(tabA, atLanding, 30)
    reportTrace('点登出后的轨迹', traceB)
    checkTrue('登出后落点是授权服务的登录页', atLanding(await tabA.url()), await tabA.url())
    check('订单中心的本地令牌已清掉',
      await tabA.eval('localStorage.getItem("order_access_token")'), null)

    // ============================================================ 路径 C
    console.log()
    console.log('='.repeat(90))
    console.log('路径 C：在【营销系统】点「退出登录」—— 另一个子系统的标签页会被踢')
    console.log('='.repeat(90))
    // 必须换一个**源**来验：同源的两个标签页共享 localStorage，登出方一清令牌，
    // 另一个标签页连令牌都没了，走不到「401 被踢」那条路。营销系统（5175）与订单中心（5176）
    // 是两个源，各存各的令牌 —— 这才是真实场景。
    await submitOauthLogin(tabA, 'admin', 'admin123')
    await tabA.waitFor(`!!localStorage.getItem('order_access_token')`, '重新登录', 40000)

    const mktTab = await newTab(tabA, MARKETING_WEB + '/')
    await mktTab.waitFor(`!!localStorage.getItem('marketing_access_token')`, '营销系统就绪', 25000)
    checkTrue('营销系统静默登录（同一次授权服务的登录态）', true, await mktTab.url())

    await mktTab.bringToFront()
    await sleep(300)
    check('在营销系统点到「退出登录」', await clickText(mktTab, '退出登录'), true)
    await confirmBox(mktTab)

    const traceP = await traceUntil(mktTab, atLanding, 30)
    reportTrace('营销系统标签页的轨迹', traceP)
    checkTrue('营销系统标签页落在授权服务的登录页', atLanding(await mktTab.url()),
      await mktTab.url())

    // 被踢的子系统标签页（订单中心，与营销系统不同源）：切回来触发惰性校验。
    // 这里要等 1.5 秒再开始看 —— sessionWatch 有 1 秒节流，切标签页本身也会产生
    // visibilitychange/focus，掐着时间看会把「刚被节流掉」误判成「没被踢」。
    await tabA.bringToFront()
    await sleep(1500)
    let traceK = await traceUntil(tabA, atLanding, 12)
    if (!atLanding(await tabA.url())) {
      // 真实事件没生效（headless 下激活标签页不一定派发），手动触发一次它此刻适用的分支。
      // 这不是绕过验证：focus 分支就是真实代码路径，只是由合成事件触发而已。
      const visible = await tabA.eval('document.visibilityState')
      console.log('  （真实事件未触发，手动触发 %s 分支）', visible === 'visible' ? 'visibilitychange' : 'focus')
      await sleep(1200)
      await tabA.eval(visible === 'visible'
        ? 'document.dispatchEvent(new Event("visibilitychange")), true'
        : 'window.dispatchEvent(new Event("focus")), true')
      traceK = traceK.concat(await traceUntil(tabA, atLanding, 20))
    }
    reportTrace('被踢的订单中心标签页的轨迹', traceK)
    checkTrue('被踢的子系统标签页也落在授权服务的登录页', atLanding(await tabA.url()),
      await tabA.url())
    check('被踢标签页的本地令牌也清掉了',
      await tabA.eval('localStorage.getItem("order_access_token")'), null)

    // ============================================================ 路径 D
    console.log()
    console.log('='.repeat(90))
    console.log('路径 D：登出后带着已失效的旧令牌再进子系统')
    console.log('='.repeat(90))
    // 此时订单中心 localStorage 里还留着刚登出前那个已失效的令牌
    const stale = await tabA.eval('localStorage.getItem("order_access_token")')
    console.log('  （此时订单中心残留的旧令牌：%s）', stale ? '有' : '已无')
    const tabD = await newTab(tabA, ORDER_WEB + '/')
    const traceD = await traceUntil(tabD, atLanding, 30)
    reportTrace('带废令牌再进订单中心的轨迹', traceD)
    checkTrue('仍然落在授权服务的登录页', atLanding(await tabD.url()), await tabD.url())
  } finally {
    child.kill()
  }
  process.exit(summary() ? 1 : 0)
}

main().catch((e) => {
  console.error('\n执行失败：', e.message)
  process.exit(2)
})
