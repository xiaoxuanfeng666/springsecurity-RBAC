import request from './request'

/** 在线用户列表（来自 Redis 里的 SSO 会话） */
export function listSsoSessions() {
  return request.get('/sso/session')
}

/** 强制某用户下线：删 SSO 会话 + 销毁其浏览器会话，各子系统令牌即刻失效 */
export function kickSsoSession(userId) {
  return request.delete(`/sso/session/${userId}`)
}
