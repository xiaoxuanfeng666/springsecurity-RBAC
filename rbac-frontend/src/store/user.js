import { defineStore } from 'pinia'
import { loginByCode as loginByCodeApi, logout as logoutApi, getInfo as getInfoApi } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    userInfo: null,
    roles: [],
    permissions: []
  }),

  actions: {
    /**
     * 统一 SSO 登录（本系统唯一的登录方式）
     * <p>
     * 登录页跳到 rbac-oauth，认证完成后带 code 回到 /oauth/callback，
     * 再由回调页调这里建立会话。令牌由后端保管，浏览器只拿到 HttpOnly Cookie。
     */
    async loginByCode(code, state) {
      const res = await loginByCodeApi(code, state)
      this.applyInfo(res.data)
      return res
    },

    applyInfo(info) {
      this.userInfo = info.user
      this.roles = info.roles || []
      this.permissions = info.permissions || []
    },

    async getInfo() {
      const res = await getInfoApi()
      this.applyInfo(res.data)
      return res.data
    },

    /**
     * 退出登录：调后台接口写操作日志，并销毁授权服务的 SSO 会话与本系统的 BFF 会话
     * <p>
     * 只清前端状态；清授权服务的浏览器登录态还需要随后整页跳转，见 {@code utils/sso.js}。
     */
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 会话已失效时后台可能回 401，不影响继续走统一登出
      }
    },

    resetState() {
      this.userInfo = null
      this.roles = []
      this.permissions = []
    },

    hasPerm(perm) {
      if (!perm) return true
      return this.permissions.includes('*:*:*') || this.permissions.includes(perm)
    },

    hasAnyPerm(perms) {
      if (!perms || perms.length === 0) return true
      return perms.some((p) => this.hasPerm(p))
    }
  }
})
