import request from './request'

export function getMenuTree() {
  return request.get('/menu/tree')
}

export function getMenu(id) {
  return request.get(`/menu/${id}`)
}

export function createMenu(data) {
  return request.post('/menu', data)
}

export function updateMenu(data) {
  return request.put('/menu', data)
}

export function deleteMenu(id) {
  return request.delete(`/menu/${id}`)
}
