import { getPortalInfo } from '@/api/portal'
import { getAccessToken } from '@/utils/auth'

/** 切换标签页时 visibilitychange 与 focus 会连着触发，这里把同一轮折叠成一次 */
const THROTTLE_MILLIS = 1000

let started = false
let lastCheckedAt = 0
let onVisibilityChange = null
let onFocus = null

/**
 * 切回门户标签页时，顺手确认一次登录态还有效
 * <p>
 * 门户是「统一登出」的入口，但也可能是**被登出的那一端**：管理员在 RBAC 后台把这个人踢下线，
 * 或者他本人在别的浏览器/别的入口登出，门户这边不会收到任何通知。
 * 各系统跑在不同的源上，彼此之间没有通道，唯一能知道的方式就是去问一次服务端。
 * <p>
 * 时机选在「切回标签页」：定时轮询会在用户根本没看这个页面时也持续发请求，
 * 而切回来的那一刻已经覆盖了绝大多数会让用户觉得「怎么还登录着」的场景。
 * 请求走同一个 axios 实例，401 由响应拦截器统一清凭证并跳登录页，本模块不重复实现那套判定。
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
  getPortalInfo().catch(() => {})
}
