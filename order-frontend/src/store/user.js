import { defineStore } from 'pinia'
import { loginByCode as loginByCodeApi, getInfo as getInfoApi } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    userInfo: null,
    roles: [],
    permissions: []
  }),

  actions: {
    /**
     * 授权码模式登录（本系统唯一的登录方式）
     * <p>
     * 后端用 code 换到令牌后自己保管，只返回用户信息；浏览器不接触任何令牌。
     */
    async loginByCode(code, state) {
      const res = await loginByCodeApi(code, state)
      this.applyInfo(res.data)
      return res
    },

    applyInfo(info) {
      this.userInfo = {
        userId: info.userId,
        username: info.username,
        nickname: info.nickname
      }
      this.roles = info.roles || []
      this.permissions = info.permissions || []
    },

    async getInfo() {
      const res = await getInfoApi()
      this.applyInfo(res.data)
      return res.data
    },

    /**
     * 登出（仅清本地状态）
     * <p>
     * 真正的登出在服务端：按钮走的是 {@code utils/sso.js}（后端销毁会话 + 整页跳转）。
     * 这里只把前端状态抹掉 —— 也供路由守卫等「会话已失效」的场景使用。
     */
    logout() {
      this.resetState()
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
