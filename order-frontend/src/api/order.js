import request from './request'

/** 订单分页 */
export function pageOrders(params) {
  return request({ url: '/order/page', method: 'get', params })
}

/** 订单详情 */
export function getOrder(id) {
  return request({ url: `/order/${id}`, method: 'get' })
}

export function createOrder(data) {
  return request({ url: '/order', method: 'post', data })
}

export function updateOrder(data) {
  return request({ url: '/order', method: 'put', data })
}

export function deleteOrder(id) {
  return request({ url: `/order/${id}`, method: 'delete' })
}

/** 订单统计（只读汇总） */
export function getOrderStat() {
  return request({ url: '/order/stat', method: 'get' })
}
