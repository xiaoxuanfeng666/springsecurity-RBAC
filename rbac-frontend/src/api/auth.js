import request from './request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function getInfo() {
  return request.get('/auth/info')
}

export function kickOut(userId) {
  return request.delete(`/auth/kick/${userId}`)
}

export function changePassword(data) {
  return request.put('/auth/password', data)
}
