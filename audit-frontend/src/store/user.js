import { defineStore } from 'pinia'
import { loginByCode as loginByCodeApi, getInfo as getInfoApi } from '@/api/auth'
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: getAccessToken() || '',
    refreshToken: getRefreshToken() || '',
    userInfo: null,
    roles: [],
    permissions: []
  }),

  actions: {
    /**
     * 授权码模式登录（本系统唯一的登录方式）
     * <p>
     * 登录入口在统一门户：未登录访问本系统会先跳到门户，由门户完成认证后把浏览器送回
     * 本系统的授权地址，本系统回调页拿到 code 再调这里换令牌。之前那条「账号密码登录」
     * （密码模式）已经去掉 —— 那种方式浏览器里不会留下授权服务的 SESSION，
     * 会让统一登出多出一整类特例（详见 utils/sso.js 的说明）。
     */
    async loginByCode(code) {
      const res = await loginByCodeApi(code)
      this.applyTokens(res.data)
      return res
    },

    applyTokens({ accessToken, refreshToken }) {
      setTokens({ accessToken, refreshToken })
      this.accessToken = accessToken
      this.refreshToken = refreshToken
    },

    async getInfo() {
      const res = await getInfoApi()
      const info = res.data
      this.userInfo = {
        userId: info.userId,
        username: info.username,
        nickname: info.nickname
      }
      this.roles = info.roles || []
      this.permissions = info.permissions || []
      return info
    },

    /**
     * 登出（仅清本地凭证）
     * <p>
     * 真正的「登出」在服务端：令牌由 rbac-oauth 签发、自包含，本系统删不掉它，
     * 删除 SSO 会话才会让它立刻失效。因此登出按钮走的是 {@code utils/sso.js} 的整页跳转，
     * 这里只负责把本地登录态抹掉 —— 也供路由守卫等「令牌已失效、需要安静地清掉」的场景使用。
     */
    logout() {
      this.resetState()
    },

    resetState() {
      clearTokens()
      this.accessToken = ''
      this.refreshToken = ''
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
