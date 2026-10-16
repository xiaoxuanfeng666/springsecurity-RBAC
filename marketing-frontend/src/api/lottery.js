import request from './request'

/** 抽奖活动分页 */
export function pageActivities(params) {
  return request({ url: '/activity/page', method: 'get', params })
}

/** 活动详情 */
export function getActivity(id) {
  return request({ url: `/activity/${id}`, method: 'get' })
}

/** 全部活动（不分页），抽奖记录页的下拉用 */
export function listActivities() {
  return request({ url: '/activity/list', method: 'get' })
}

export function createActivity(data) {
  return request({ url: '/activity', method: 'post', data })
}

export function updateActivity(data) {
  return request({ url: '/activity', method: 'put', data })
}

export function deleteActivity(id) {
  return request({ url: `/activity/${id}`, method: 'delete' })
}

/** 活动的奖品配置：全部启用奖品 + 该活动已配置的概率与数量 */
export function getActivityPrizes(id) {
  return request({ url: `/activity/${id}/prizes`, method: 'get' })
}

/** 保存活动的奖品配置（整体替换） */
export function updateActivityPrizes(id, data) {
  return request({ url: `/activity/${id}/prizes`, method: 'put', data })
}

/** 奖品配置分页 */
export function pagePrizes(params) {
  return request({ url: '/prize/page', method: 'get', params })
}

export function listPrizes() {
  return request({ url: '/prize/list', method: 'get' })
}

export function getPrize(id) {
  return request({ url: `/prize/${id}`, method: 'get' })
}

export function createPrize(data) {
  return request({ url: '/prize', method: 'post', data })
}

export function updatePrize(data) {
  return request({ url: '/prize', method: 'put', data })
}

export function deletePrize(id) {
  return request({ url: `/prize/${id}`, method: 'delete' })
}

/** 抽奖记录分页（只读） */
export function pageRecords(params) {
  return request({ url: '/record/page', method: 'get', params })
}
