import request from './request'

export function pageSsoSessions(params) {
  return request.get('/sso/session/page', { params })
}

export function kickSsoSession(sessionId) {
  return request.post(`/sso/session/kick/${sessionId}`)
}
