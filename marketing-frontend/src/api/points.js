import request from './request'

/** 积分任务分页 */
export function pageTasks(params) {
  return request({ url: '/task/page', method: 'get', params })
}

export function getTask(id) {
  return request({ url: `/task/${id}`, method: 'get' })
}

export function createTask(data) {
  return request({ url: '/task', method: 'post', data })
}

export function updateTask(data) {
  return request({ url: '/task', method: 'put', data })
}

export function deleteTask(id) {
  return request({ url: `/task/${id}`, method: 'delete' })
}

/** 积分流水分页（只读） */
export function pageFlows(params) {
  return request({ url: '/points/page', method: 'get', params })
}
