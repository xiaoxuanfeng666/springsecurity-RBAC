import { checkTrue, summary, launchChrome, firstTab } from './cdp.mjs'

const WEB = 'http://localhost:5174'
const RETURN_TO = encodeURIComponent('/oauth/authorize?client_id=rbac-third-party&response_type=code&scope=read')

async function main() {
  const { child } = await launchChrome(9336)
  const tab = await firstTab(9336)
  try {
    await tab.goto(`${WEB}/login?returnTo=${RETURN_TO}`)
    await tab.waitFor('!!document.querySelector(".qr-image")', '二维码渲染', 20000)
    await tab.waitFor('!!document.querySelector(".password-col")', '账号密码栏渲染', 5000)

    const wide = await tab.eval(`(() => {
      const q = document.querySelector('.qr-col').getBoundingClientRect();
      const p = document.querySelector('.password-col').getBoundingClientRect();
      const text = document.body.innerText;
      return {
        qLeft: Math.round(q.left), qTop: Math.round(q.top), qRight: Math.round(q.right),
        pLeft: Math.round(p.left), pTop: Math.round(p.top),
        hasQrTitle: text.includes('企业微信扫码登录'),
        hasPwdTitle: text.includes('账号密码登录'),
        hasDivider: !!document.querySelector('.login-divider')
      };
    })()`)
    checkTrue('左侧显示扫码登录', wide.hasQrTitle)
    checkTrue('右侧显示账号密码登录', wide.hasPwdTitle)
    checkTrue('中间有竖向分隔线', wide.hasDivider)
    checkTrue('两栏左右排列', wide.qRight <= wide.pLeft && Math.abs(wide.qTop - wide.pTop) < 30,
      JSON.stringify({ qr: [wide.qLeft, wide.qTop, wide.qRight], pwd: [wide.pLeft, wide.pTop] }))
    console.log('宽屏截图：%s', await tab.screenshot('login-two-columns.png'))

    await tab.send('Emulation.setDeviceMetricsOverride',
      { width: 600, height: 900, deviceScaleFactor: 1, mobile: false })
    await new Promise((r) => setTimeout(r, 600))
    const narrow = await tab.eval(`(() => {
      const q = document.querySelector('.qr-col').getBoundingClientRect();
      const p = document.querySelector('.password-col').getBoundingClientRect();
      return { qBottom: Math.round(q.bottom), pTop: Math.round(p.top) };
    })()`)
    checkTrue('窄屏改为上下堆叠', narrow.pTop >= narrow.qBottom - 20,
      JSON.stringify(narrow))
    console.log('窄屏截图：%s', await tab.screenshot('login-two-columns-narrow.png'))
  } finally {
    try { tab.ws.close() } catch (e) { /* ignore */ }
    try { child.kill() } catch (e) { /* ignore */ }
  }
  process.exitCode = summary() === 0 ? 0 : 1
}

main().catch((e) => {
  console.error('验证失败：', e)
  process.exitCode = 1
})
