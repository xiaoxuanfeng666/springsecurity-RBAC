import request from './request'

export function pageRoles(params) {
  return request.get('/role/page', { params })
}

export function listAllRoles() {
  return request.get('/role/all')
}

export function getRole(id) {
  return request.get(`/role/${id}`)
}

export function getRoleMenuIds(id) {
  return request.get(`/role/${id}/menus`)
}

export function createRole(data) {
  return request.post('/role', data)
}

export function updateRole(data) {
  return request.put('/role', data)
}

/** 动态修改角色权限 */
export function updateRoleMenus(id, menuIds) {
  return request.put(`/role/${id}/menus`, menuIds)
}

export function deleteRole(id) {
  return request.delete(`/role/${id}`)
}
