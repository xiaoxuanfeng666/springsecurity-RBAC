import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getInfo as getInfoApi } from '@/api/auth'
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
    async login(form) {
      const res = await loginApi(form)
      const { accessToken, refreshToken } = res.data
      setTokens({ accessToken, refreshToken })
      this.accessToken = accessToken
      this.refreshToken = refreshToken
      return res
    },

    async getInfo() {
      const res = await getInfoApi()
      this.userInfo = res.data.user
      this.roles = res.data.roles || []
      this.permissions = res.data.permissions || []
      return res.data
    },

    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 登出接口失败也要清理本地状态
      }
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
