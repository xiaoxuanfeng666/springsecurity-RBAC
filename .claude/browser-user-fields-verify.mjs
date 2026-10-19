import {
  check, checkTrue, summary, sleep, launchChrome, newTab
} from './cdp.mjs'

const WEB = 'http://localhost:5173'

async function login(tab) {
  await tab.goto(`${WEB}/login`)
  await tab.waitFor('document.querySelectorAll("input").length >= 2', '登录表单')
  await tab.eval(`(() => {
    const ins = document.querySelectorAll('input');
    const set = (el, v) => {
      el.value = v;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    };
    set(ins[0], 'admin');
    set(ins[1], 'admin123');
    return true;
  })()`)
  await tab.eval(`(() => {
    const btn = document.querySelector('.login-button');
    btn.click();
    return true;
  })()`)
  await tab.waitFor('location.pathname !== "/login"', '登录成功离开登录页', 20000)
}

async function main() {
  const { child, profile } = await launchChrome()
  console.log('Chrome 已启动（%s）', profile)
  let tab
  try {
    tab = await newTab(await (async () => {
      const { firstTab } = await import('./cdp.mjs')
      return firstTab()
    })())
    await login(tab)
    await tab.goto(`${WEB}/system/user`)
    await tab.waitFor(
      'document.querySelectorAll(".el-table__body tbody tr, .el-table__body tr").length > 0',
      '用户列表渲染', 20000)

    const header = await tab.eval(`document.querySelector('.el-table__header')?.innerText || ''`)
    checkTrue('列表包含「工号」列', header.includes('工号'), header.replace(/\\s+/g, ' ').slice(0, 120))
    checkTrue('列表包含「职务」列', header.includes('职务'))
    checkTrue('列表包含「部门」列', header.includes('部门'))

    const row = await tab.eval(`(() => {
      const rows = [...document.querySelectorAll('.el-table__body tr')];
      const r = rows.find((x) => x.innerText.includes('verify_user_1018'));
      return r ? r.innerText : null;
    })()`)
    checkTrue('找到验证用户行', !!row, row ? row.replace(/\\s+/g, ' ').slice(0, 160) : 'null')
    if (row) {
      checkTrue('行内显示工号 E10086', row.includes('E10086'))
      checkTrue('行内显示职务', row.includes('高级测试工程师'))
      checkTrue('行内显示多个部门', row.includes('研发部') && row.includes('市场部') && row.includes('前端组'))
    }

    const clicked = await tab.eval(`(() => {
      const rows = [...document.querySelectorAll('.el-table__body tr')];
      const r = rows.find((x) => x.innerText.includes('verify_user_1018'));
      if (!r) return false;
      const btn = [...r.querySelectorAll('button')].find((b) => b.innerText.trim() === '修改');
      if (!btn) return false;
      btn.click();
      return true;
    })()`)
    checkTrue('点击「修改」打开弹窗', clicked)
    await tab.waitFor('!!document.querySelector(".el-dialog")', '编辑弹窗', 5000)
    await tab.waitFor(
      'document.querySelector(".el-dialog").innerText.includes("工号")',
      '编辑弹窗渲染新字段', 5000)

    const dialog = await tab.eval(`(() => {
      const d = document.querySelector('.el-dialog');
      const inputs = [...d.querySelectorAll('input')].map((i) => i.value);
      const areas = [...d.querySelectorAll('textarea')].map((t) => t.value);
      return { text: d.innerText, inputs, areas };
    })()`)
    const text = dialog.text
    for (const label of ['工号', '别名', '性别', '职务', '主部门', '兼任部门', '部门负责人',
      '部门内排序', '直属上级', '企业邮箱', '座机', '对外职务', '对外昵称', '地址', '扩展属性']) {
      checkTrue(`弹窗包含「${label}」`, text.includes(label))
    }
    checkTrue('工号回填 E10086', dialog.inputs.includes('E10086'))
    checkTrue('别名回填 verify', dialog.inputs.includes('verify'))
    checkTrue('职务回填高级测试工程师', dialog.inputs.includes('高级测试工程师'))
    checkTrue('企业邮箱回填', dialog.inputs.includes('verify@corp.example.com'))
    checkTrue('座机回填', dialog.inputs.includes('010-88886666'))
    checkTrue('主部门回填研发部', text.includes('研发部'))
    checkTrue('兼任部门回填市场部/前端组', text.includes('市场部') && text.includes('前端组'))
    checkTrue('扩展属性回填 JSON', dialog.areas.some((v) => v.includes('office')))

    const shot = await tab.screenshot('user-fields-verify.png')
    console.log('截图：%s', shot)
  } finally {
    try { if (tab) tab.ws.close() } catch (e) { /* ignore */ }
    try { child.kill() } catch (e) { /* ignore */ }
  }
  process.exitCode = summary() === 0 ? 0 : 1
}

main().catch((e) => {
  console.error('验证失败：', e)
  process.exitCode = 1
})
