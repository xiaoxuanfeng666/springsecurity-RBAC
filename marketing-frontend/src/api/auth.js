import request from './request'

/** 密码模式登录：账号密码由营销后端转给 rbac-oauth 校验 */
export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

/** 授权码模式登录：把 rbac-oauth 回调带回来的 code 换成令牌 */
export function loginByCode(code) {
  return request({
    url: '/auth/code',
    method: 'post',
    data: { code }
  })
}

/** 授权码模式的跳转地址（由后端拼装，避免 client_id / redirect_uri 在前端散落） */
export function getAuthorizeUrl(state) {
  return request({
    url: '/auth/authorize-url',
    method: 'get',
    params: { state }
  })
}

/** 当前用户信息：权限点 + 菜单树 */
export function getInfo() {
  return request({
    url: '/auth/info',
    method: 'get'
  })
}
