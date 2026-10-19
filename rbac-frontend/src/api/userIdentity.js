import request from './request'

export function listIdentityProviders() {
  return request.get('/user/identities/providers')
}

export function listUserIdentities(userId) {
  return request.get(`/user/${userId}/identities`)
}

export function createUserIdentity(userId, data) {
  return request.post(`/user/${userId}/identities`, data)
}

export function deleteUserIdentity(id) {
  return request.delete(`/user/identity/${id}`)
}
