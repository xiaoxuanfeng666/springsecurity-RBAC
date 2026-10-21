import request from './request'

/**
 * 统一 SSO 登录：用授权服务回调带回来的 code 建立服务端会话
 * <p>
 * 返回的是用户信息（身份 + 角色 + 权限 + 菜单），不是令牌 —— 令牌由后端保管，
 * 浏览器只拿到一个 HttpOnly 会话 Cookie。
 */
export function loginByCode(code, state) {
  return request.post('/auth/code', { code, state })
}

/** 登出：销毁授权服务的 SSO 会话与本系统的 BFF 会话，并记一条操作日志 */
export function logout() {
  return request.post('/auth/logout')
}

export function getInfo() {
  return request.get('/auth/info')
}

export function kickOut(userId) {
  return request.delete(`/auth/kick/${userId}`)
}

export function changePassword(data) {
  return request.put('/auth/password', data)
}
