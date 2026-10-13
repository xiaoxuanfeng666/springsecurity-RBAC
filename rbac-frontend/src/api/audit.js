import request from './request'

export function pageAudits(params) {
  return request.get('/audit/page', { params })
}
