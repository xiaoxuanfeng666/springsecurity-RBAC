import request from './request'

export function pageClients(params) {
  return request.get('/client/page', { params })
}

export function getClient(appId) {
  return request.get(`/client/${appId}`)
}

export function createClient(data) {
  return request.post('/client', data)
}

export function updateClient(data) {
  return request.put('/client', data)
}

export function deleteClient(appId) {
  return request.delete(`/client/${appId}`)
}
