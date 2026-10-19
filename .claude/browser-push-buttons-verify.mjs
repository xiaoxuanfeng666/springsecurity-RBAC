import { checkTrue, summary, launchChrome, firstTab } from './cdp.mjs'

const WEB = 'http://localhost:5173'

async function main() {
  const { child } = await launchChrome(9334)
  const tab = await firstTab(9334)
  try {
    await tab.goto(`${WEB}/login`)
    await tab.waitFor('document.querySelectorAll("input").length >= 2', '登录表单')
    await tab.eval(`(() => {
      const ins = document.querySelectorAll('input');
      const set = (el, v) => {
        el.value = v;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      };
      set(ins[0], 'admin'); set(ins[1], 'admin123');
      return true;
    })()`)
    await tab.eval('document.querySelector(".login-button").click()')
    await tab.waitFor('location.pathname !== "/login"', '登录成功', 20000)
    await tab.goto(`${WEB}/system/user`)
    await tab.waitFor('document.querySelectorAll(".el-table__body tr").length > 0', '用户列表', 20000)

    const text = await tab.eval('document.body.innerText')
    checkTrue('工具栏有全量推送按钮', text.includes('推送全部到企业微信'))
    checkTrue('行内有单用户推送按钮', text.includes('推送企微'))
    const shot = await tab.screenshot('user-push-buttons.png')
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
