import { defineStore } from 'pinia'
import { login as loginApi, loginByCode as loginByCodeApi, getInfo as getInfoApi } from '@/api/auth'
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
    /** 密码模式登录 */
    async login(form) {
      const res = await loginApi(form)
      this.applyTokens(res.data)
      return res
    },

    /** 授权码模式登录 */
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
     * 登出
     * <p>
     * 本系统没有服务端会话可删：令牌由 rbac-oauth 签发，这里清掉本地凭证即可
     * （令牌本身要等自然过期，或在 RBAC 里把用户停用/离职后由下一次同步拒掉）。
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
