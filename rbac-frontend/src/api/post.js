import request from './request'

export function pagePosts(params) {
  return request.get('/post/page', { params })
}

export function listAllPosts() {
  return request.get('/post/list')
}

export function getPost(id) {
  return request.get(`/post/${id}`)
}

export function createPost(data) {
  return request.post('/post', data)
}

export function updatePost(data) {
  return request.put('/post', data)
}

export function deletePost(id) {
  return request.delete(`/post/${id}`)
}
