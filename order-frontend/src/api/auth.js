import request from './request'

/**
 * 授权码模式登录：把 rbac-oauth 回调带回来的 code 交给后端建立会话
 * <p>
 * 返回的是用户信息（身份 + 角色 + 权限 + 菜单），不是令牌 —— 令牌由后端保管，
 * 浏览器只拿到一个 HttpOnly 会话 Cookie。
 */
export function loginByCode(code, state) {
  return request({
    url: '/auth/code',
    method: 'post',
    data: { code, state }
  })
}

/** 登出：由后端销毁授权服务的 SSO 会话与本系统的 BFF 会话 */
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

/** 当前用户信息：权限点 + 菜单树 */
export function getInfo() {
  return request({
    url: '/auth/info',
    method: 'get'
  })
}
