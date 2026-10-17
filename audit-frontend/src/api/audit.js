import request from './request'

/** 审计日志分页查询 */
export function pageAudits(params) {
  return request.get('/audit/page', { params })
}

/** 单条详情：含完整请求参数 */
export function getAuditDetail(id) {
  return request.get(`/audit/${id}`)
}
