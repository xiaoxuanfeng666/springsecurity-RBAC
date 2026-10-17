import { getInfo } from '@/api/auth'
import { getAccessToken } from '@/utils/auth'

/** 切换标签页时 visibilitychange 与 focus 会连着触发，这里把同一轮折叠成一次 */
const THROTTLE_MILLIS = 1000

let started = false
let lastCheckedAt = 0
let onVisibilityChange = null
let onFocus = null

/**
 * 切回本系统标签页时，顺手确认一次登录态还有效
 * <p>
 * 背景：统一登出在服务端是**立即**生效的（登出瞬间该用户所有子系统的令牌就作废了），
 * 但各子系统跑在不同的源上，彼此之间没有任何通道，本系统不会「被告知」这件事。
 * 默认表现为「下次请求才被踢回登录页」—— 如果用户切回来只是看着不点任何东西，
 * 看到的还是登录状态下的页面，很容易以为登出没生效。
 * <p>
 * 所以这里补一次**惰性校验**：用户切回这个标签页（或把窗口切回来）时，悄悄请求一次
 * {@code /auth/info}。请求走的是同一个 axios 实例，因此 401 会被既有的响应拦截器统一处理 ——
 * 先尝试用 Refresh Token 续期（access token 只是过期时静默续上，用户无感），
 * 续期被拒（会话已结束）就清凭证并跳登录页。本模块只负责「制造一次请求」，不重复实现那套判定。
 *
 * <h3>为什么不轮询</h3>
 * 定时轮询会在用户根本不在看这个页面时也持续发请求；而「切回标签页」这个时机已经覆盖了
 * 绝大多数会让用户产生「怎么还登录着」疑问的场景，且代价是零。
 */
export function startSessionWatch() {
  if (started) {
    return
  }
  started = true
  onVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
      revalidate()
    }
  }
  onFocus = revalidate
  document.addEventListener('visibilitychange', onVisibilityChange)
  // 两个窗口并排、本标签页一直可见时 visibilitychange 不触发，靠 focus 兜住
  window.addEventListener('focus', onFocus)
}

export function stopSessionWatch() {
  if (!started) {
    return
  }
  started = false
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('focus', onFocus)
  onVisibilityChange = null
  onFocus = null
}

function revalidate() {
  // 登录页（或已登出）没有令牌可校验，也就不该发这个请求
  if (!getAccessToken()) {
    return
  }
  const now = Date.now()
  if (now - lastCheckedAt < THROTTLE_MILLIS) {
    return
  }
  lastCheckedAt = now
  // 结果不在这里处理：成功无事发生，失败交给响应拦截器（含跳转登录页）
  getInfo().catch(() => {})
}
