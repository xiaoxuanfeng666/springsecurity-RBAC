import { checkTrue, summary, launchChrome, firstTab } from './cdp.mjs'

const WEB = 'http://localhost:5174'
const RETURN_TO = encodeURIComponent('/oauth/authorize?client_id=rbac-third-party&response_type=code&scope=read')

async function main() {
  const { child } = await launchChrome(9335)
  const tab = await firstTab(9335)
  try {
    await tab.goto(`${WEB}/login?returnTo=${RETURN_TO}`)
    await tab.waitFor('!!document.querySelector(".qr-image")', '二维码图片渲染', 20000)
    const info = await tab.eval(`(() => {
      const img = document.querySelector('.qr-image');
      const text = document.body.innerText;
      return {
        srcPrefix: img ? img.src.slice(0, 22) : '',
        width: img ? img.naturalWidth : 0,
        hasTitle: text.includes('企业微信扫码登录'),
        hasCountdown: /\\d+ 秒后失效/.test(text),
        hasPassword: text.includes('或使用账号密码')
      };
    })()`)
    checkTrue('二维码图片已生成', info.srcPrefix.startsWith('data:image/png'), info.srcPrefix)
    checkTrue('二维码尺寸有效', info.width > 100, String(info.width))
    checkTrue('显示企业微信扫码标题', info.hasTitle)
    checkTrue('显示有效期倒计时', info.hasCountdown)
    checkTrue('保留账号密码登录', info.hasPassword)
    const shot = await tab.screenshot('wecom-qr-login.png')
    console.log('截图：%s', shot)
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
