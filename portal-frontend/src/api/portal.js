import request from './request'

/**
 * 用授权码换门户令牌（换令牌要带 client_secret，只能由后端做）
 * <p>
 * 登录地址不在这里取：门户登录页直接导航到后端的 {@code /portal/sso-login}，
 * 由后端拼好带密钥的授权地址并 302 过去 —— 前端拿不到 client_secret。
 */
export function exchangeToken(code) {
  return request({
    url: '/portal/token',
    method: 'post',
    data: { code }
  })
}

/** 可进入的子系统清单（含各自的授权地址） */
export function getSubsystems() {
  return request({
    url: '/portal/subsystems',
    method: 'get'
  })
}

/** 当前登录状态：身份 + 已登录过哪些子系统（一键登录靠它判断进度） */
export function getPortalInfo() {
  return request({
    url: '/portal/info',
    method: 'get'
  })
}
