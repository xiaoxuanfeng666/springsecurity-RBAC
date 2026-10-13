import request from './request'

export function getDeptTree() {
  return request.get('/dept/tree')
}

export function getDept(id) {
  return request.get(`/dept/${id}`)
}

export function createDept(data) {
  return request.post('/dept', data)
}

export function updateDept(data) {
  return request.put('/dept', data)
}

export function deleteDept(id) {
  return request.delete(`/dept/${id}`)
}
