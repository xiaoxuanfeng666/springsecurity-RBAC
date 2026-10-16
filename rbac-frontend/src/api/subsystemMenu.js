import request from './request'

/** 指定子系统的菜单树（菜单管理页与角色授权树共用） */
export function getSubsystemMenuTree(subsystemId) {
  return request.get('/subsystem-menu/tree', { params: { subsystemId } })
}

export function getSubsystemMenu(id) {
  return request.get(`/subsystem-menu/${id}`)
}

export function createSubsystemMenu(data) {
  return request.post('/subsystem-menu', data)
}

export function updateSubsystemMenu(data) {
  return request.put('/subsystem-menu', data)
}

export function deleteSubsystemMenu(id) {
  return request.delete(`/subsystem-menu/${id}`)
}
