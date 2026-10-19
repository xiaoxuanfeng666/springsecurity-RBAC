import request from './request'

export function listOutboundProviders() {
  return request.get('/idp/outbound/providers')
}

export function syncAllToProvider(provider) {
  return request.post('/idp/outbound/sync-all', null, { params: { provider } })
}

export function syncUserToProvider(userId, provider) {
  return request.post(`/idp/outbound/user/${userId}`, null, { params: { provider } })
}
