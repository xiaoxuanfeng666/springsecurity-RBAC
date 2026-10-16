import request from './request'

export function pageSubsystems(params) {
  return request.get('/subsystem/page', { params })
}

export function listSubsystems() {
  return request.get('/subsystem/list')
}

export function getSubsystem(id) {
  return request.get(`/subsystem/${id}`)
}

export function createSubsystem(data) {
  return request.post('/subsystem', data)
}

export function updateSubsystem(data) {
  return request.put('/subsystem', data)
}

export function deleteSubsystem(id) {
  return request.delete(`/subsystem/${id}`)
}
