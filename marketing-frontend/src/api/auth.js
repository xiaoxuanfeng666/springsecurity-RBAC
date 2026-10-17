import request from './request'

/**
 * 授权码模式登录：把 rbac-oauth 回调带回来的 code 换成令牌
 * <p>
 * 本系统没有账号密码登录接口了 —— 用户凭据只在 rbac-oauth 的登录页输入。
 * 后端 {@code /auth/login}（密码模式）仍然保留，供第三方接入与联调使用，只是本前端不再调它。
 */
export function loginByCode(code) {
  return request({
    url: '/auth/code',
    method: 'post',
    data: { code }
  })
}

/** 当前用户信息：权限点 + 菜单树 */
export function getInfo() {
  return request({
    url: '/auth/info',
    method: 'get'
  })
}
