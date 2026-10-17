// 与 rbac-frontend / order-frontend / marketing-frontend 的键名刻意区分：
// 多个前端可能同时开在同一个浏览器里，用同一组 localStorage 键会互相覆盖登录态。
const ACCESS_TOKEN_KEY = 'portal_access_token'

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setAccessToken(accessToken) {
  if (accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  }
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}
