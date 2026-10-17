// 与 rbac-frontend 的键名刻意区分：两个前端可能同时开在同一个浏览器里，
// 用同一组 localStorage 键会互相覆盖登录态。
const ACCESS_TOKEN_KEY = 'audit_access_token'
const REFRESH_TOKEN_KEY = 'audit_refresh_token'

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setAccessToken(accessToken) {
  if (accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  }
}

export function setTokens({ accessToken, refreshToken }) {
  setAccessToken(accessToken)
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  }
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}
