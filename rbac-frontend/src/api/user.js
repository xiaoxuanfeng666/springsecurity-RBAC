import request from './request'

export function pageUsers(params) {
  return request.get('/user/page', { params })
}

export function getUser(id) {
  return request.get(`/user/${id}`)
}

export function getUserRoles(id) {
  return request.get(`/user/${id}/roles`)
}

export function createUser(data) {
  return request.post('/user', data)
}

export function updateUser(data) {
  return request.put('/user', data)
}

export function updateUserStatus(id, status) {
  return request.put(`/user/${id}/status`, null, { params: { status } })
}

export function updateUserRoles(id, roleIds) {
  return request.put(`/user/${id}/roles`, roleIds)
}

export function resetUserPassword(id, password) {
  return request.put(`/user/${id}/resetPassword`, null, { params: { password } })
}

export function deleteUser(id) {
  return request.delete(`/user/${id}`)
}
