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

/** 查询角色在指定子系统下已分配的菜单 ID（子系统授权页回显） */
export function getRoleSubsystemMenuIds(id, subsystemId) {
  return request.get(`/role/${id}/subsystem-menus`, { params: { subsystemId } })
}

/** 分配角色的子系统菜单（按子系统局部替换，不影响其他子系统） */
export function updateRoleSubsystemMenus(id, data) {
  return request.put(`/role/${id}/subsystem-menus`, data)
}
