/**
 * 极简 CDP 驱动（用 Node 内置 WebSocket，不装任何依赖）+ 断言工具
 * <p>
 * 被 browser-sso-verify.mjs / browser-portal-entry-verify.mjs 共用。
 * 只做三件事：开一个 headless Chrome、给每个标签页一个 CDP 会话、把「在页面里求值」包成可轮询的形式。
 */
import { spawn } from 'node:child_process'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

export const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
export const PORT = 9333
export const AUTH_WEB = 'http://localhost:5174'
export const MARKETING_WEB = 'http://localhost:5175'
export const ORDER_WEB = 'http://localhost:5176'
export const PORTAL_WEB = 'http://localhost:5177'
export const SHOT_DIR = process.env.SHOT_DIR || tmpdir()

/** 卡片状态标签：精确数 .el-tag，别去 match 正文（说明文字里也含这些词，会误判） */
export const OK_TAGS =
  `[...document.querySelectorAll('.el-tag')].filter(t => t.innerText.trim() === '已签发令牌').length`
export const NO_TAGS =
  `[...document.querySelectorAll('.el-tag')].filter(t => t.innerText.trim() === '未登录').length`

let PASS = 0
let FAIL = 0

export function check(label, actual, expected) {
  if (JSON.stringify(actual) === JSON.stringify(expected)) {
    PASS++
    console.log('  [OK]   %s  %s', label.padEnd(56), JSON.stringify(actual))
  } else {
    FAIL++
    console.log('  [FAIL] %s  got=%s want=%s', label.padEnd(56),
      JSON.stringify(actual), JSON.stringify(expected))
  }
}

export function checkTrue(label, cond, detail = '') {
  if (cond) {
    PASS++
    console.log('  [OK]   %s  %s', label.padEnd(56), detail)
  } else {
    FAIL++
    console.log('  [FAIL] %s  %s', label.padEnd(56), detail)
  }
}

export function summary() {
  console.log()
  console.log('='.repeat(90))
  console.log('结果：%d 项通过，%d 项失败', PASS, FAIL)
  console.log('='.repeat(90))
  return FAIL
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

export class Tab {
  constructor(ws) {
    this.ws = ws
    this.seq = 0
    this.pending = new Map()
    this.navigations = []
    this.documents = []
    this.requests = []
    this.status = new Map()
    ws.addEventListener('message', (ev) => {
      let msg
      try {
        msg = JSON.parse(ev.data)
      } catch (e) {
        return
      }
      // 事件回调里抛异常会直接掀掉整个进程，包一层保证测试能跑完并给出诊断
      try {
        this.handle(msg)
      } catch (e) {
        console.log('  （忽略一个解析不了的事件 %s: %s）', msg.method, e.message)
      }
    })
  }

  handle(msg) {
    if (msg.id) {
      const p = this.pending.get(msg.id)
      if (!p) return
      this.pending.delete(msg.id)
      msg.error ? p.reject(new Error(JSON.stringify(msg.error))) : p.resolve(msg.result)
      return
    }
    if (msg.method === 'Page.frameNavigated' && !msg.params.frame.parentId) {
      this.navigations.push(msg.params.frame.url)
    }
    // 302 链在一次导航内完成，frameNavigated 只看得到终点；要看清每一跳得看 Document 请求
    if (msg.method === 'Network.requestWillBeSent') {
      this.requests.push(msg.params.request.url)
      if (msg.params.type === 'Document') {
        this.documents.push(msg.params.request.url)
      }
    }
    // responseReceived 的 URL 在 response 里，不在 request 里
    if (msg.method === 'Network.responseReceived') {
      this.status.set(msg.params.response.url, msg.params.response.status)
    }
  }

  static async connect(wsUrl, port = PORT) {
    const ws = new WebSocket(wsUrl)
    await new Promise((res, rej) => {
      ws.addEventListener('open', res, { once: true })
      ws.addEventListener('error', rej, { once: true })
    })
    const tab = new Tab(ws)
    tab.port = port
    await tab.send('Page.enable')
    await tab.send('Runtime.enable')
    await tab.send('Network.enable')
    return tab
  }

  send(method, params = {}) {
    const id = ++this.seq
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }))
  }

  async eval(expression) {
    const r = await this.send('Runtime.evaluate',
      { expression, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) {
      throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
    }
    return r.result.value
  }

  async waitFor(expression, desc, timeout = 25000) {
    const deadline = Date.now() + timeout
    let last = null
    while (Date.now() < deadline) {
      try {
        const v = await this.eval(expression)
        if (v) return v
        last = v
      } catch (e) {
        last = e.message
      }
      await sleep(200)
    }
    throw new Error(`等待超时：${desc}（最后的值：${JSON.stringify(last)}）`)
  }

  async url() {
    try {
      return await this.eval('location.href')
    } catch (e) {
      return '(导航中)'
    }
  }

  async goto(url) {
    await this.send('Page.navigate', { url })
  }

  async bringToFront() {
    await this.send('Page.bringToFront')
  }

  async screenshot(name) {
    const path = join(SHOT_DIR, name)
    const r = await this.send('Page.captureScreenshot', { format: 'png' })
    writeFileSync(path, Buffer.from(r.data, 'base64'))
    return path
  }

  /** 打印匹配片段的所有请求及其响应码 */
  logRequests(match, title) {
    const hits = this.requests.filter((u) => u.includes(match))
    console.log('  （%s：%d 条）', title, hits.length)
    hits.forEach((u) => console.log('      %s  → HTTP %s',
      u.replace(/^https?:\/\/[^/]+/, ''), this.status.get(u) ?? '?'))
  }

  /** 清空导航记录，便于只观察「某一步之后」的跳转 */
  resetTrail() {
    this.documents = []
    this.navigations = []
  }
}

export async function cdpList(port = PORT) {
  const res = await fetch(`http://127.0.0.1:${port}/json/list`)
  return res.json()
}

/**
 * 起一个 headless Chrome。
 * <p>
 * 端口可指定：验证「第二个用户」时必须另起一个实例 —— 同一实例里各标签页共享 Cookie 与
 * localStorage，「换个标签页登出再用另一个账号登」是做不到的，那样测出来的还是同一个人。
 */
export async function launchChrome(port = PORT) {
  const profile = mkdtempSync(join(tmpdir(), 'cdp-sso-'))
  const child = spawn(CHROME, [
    '--headless=new',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-gpu',
    '--window-size=1440,900',
    'about:blank'
  ], { stdio: 'ignore' })

  const deadline = Date.now() + 20000
  while (Date.now() < deadline) {
    try {
      const list = await cdpList(port)
      if (list.some((t) => t.type === 'page')) return { child, profile, port }
    } catch (e) {
      /* 还没起来 */
    }
    await sleep(300)
  }
  throw new Error(`Chrome 调试端口没起来（${port}）`)
}

/** 开一个新标签页。先在 about:blank 连上 CDP 再导航，否则会漏掉最早的几次跳转 */
export async function newTab(existingTab, url) {
  const r = await existingTab.send('Target.createTarget', { url: 'about:blank' })
  await sleep(500)
  const info = (await cdpList(existingTab.port)).find((t) => t.id === r.targetId)
  const tab = await Tab.connect(info.webSocketDebuggerUrl, existingTab.port)
  if (url) await tab.goto(url)
  return tab
}

/** 第一个标签页 */
export async function firstTab(port = PORT) {
  const pages = (await cdpList(port)).filter((t) => t.type === 'page')
  return Tab.connect(pages[0].webSocketDebuggerUrl, port)
}

/**
 * 按顺序断言 Document 请求序列里出现过这些片段。
 * 注意 302 链在一次导航内完成，这里看的是每一次「发起导航」的 URL。
 */
export function checkHopOrder(label, docs, expected) {
  let cursor = 0
  const found = []
  for (const url of docs) {
    if (cursor < expected.length && url.includes(expected[cursor].needle)) {
      found.push(expected[cursor].name)
      cursor++
    }
  }
  if (cursor === expected.length) {
    PASS++
    console.log('  [OK]   %s', label.padEnd(56))
    found.forEach((f, i) => console.log('           %d. %s', i + 1, f))
  } else {
    FAIL++
    console.log('  [FAIL] %s  只走到 %s，缺 %s', label.padEnd(56), found.join(' → '),
      expected.slice(cursor).map((e) => e.name).join(' → '))
    console.log('         实际 Document 请求：')
    docs.forEach((d) => console.log('           - %s', d))
  }
}

/** 在登录页填账号密码并提交（等价于用户手点） */
export async function submitOauthLogin(tab, username, password) {
  await tab.waitFor('document.querySelectorAll("input").length >= 2', '登录表单渲染出来')
  await tab.eval(`(() => {
    const ins = document.querySelectorAll('input');
    const set = (el, v) => { el.value = v; el.dispatchEvent(new Event('input', {bubbles:true})); };
    set(ins[0], ${JSON.stringify(username)}); set(ins[1], ${JSON.stringify(password)});
    return true;
  })()`)
  await tab.eval('document.querySelector(".oauth-submit").click()')
}

/** 读某个子系统 localStorage 里令牌的声明（只解码，不验签） */
export async function tokenClaims(tab, storageKey) {
  return tab.eval(`(() => {
    const t = localStorage.getItem(${JSON.stringify(storageKey)});
    if (!t) return null;
    const seg = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = seg + '='.repeat((4 - (seg.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  })()`)
}
