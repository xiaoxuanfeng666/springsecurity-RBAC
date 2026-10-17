import { defineStore } from 'pinia'
import { exchangeToken, getPortalInfo, getSubsystems } from '@/api/portal'
import { getAccessToken, setAccessToken, clearTokens } from '@/utils/auth'
import { originOf, takeReturnOrigin } from '@/utils/returnTarget'

/** 接口前缀：返回目标要指后端的 302 端点，用的就是这个前缀 */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: getAccessToken() || '',
    username: '',
    nickname: '',
    loginTime: '',
    /** 本次 SSO 会话已经拿到过令牌的客户端 id，由服务端（SSO 会话）记录 */
    clients: []
  }),

  getters: {
    /** 某个子系统是否已经登录过（一键登录据此判断这一步要不要做、做完了没有） */
    loggedInTo: (state) => (clientId) => state.clients.includes(clientId)
  },

  actions: {
    /** 授权码换令牌（门户后端代换，前端只拿结果） */
    async loginByCode(code) {
      const res = await exchangeToken(code)
      const token = res.data.accessToken
      setAccessToken(token)
      this.accessToken = token
      return res
    },

    /**
     * 拉取当前登录状态
     * <p>
     * clients 是服务端的事实（SSO 会话里记着这次登录进过哪些客户端），
     * 一键登录每一步都靠轮询它来判断「那一步真的完成了」，而不是去猜 iframe 加载完没有。
     */
    async loadInfo() {
      const res = await getPortalInfo()
      this.username = res.data.username || ''
      this.nickname = res.data.nickname || ''
      this.loginTime = res.data.loginTime || ''
      this.clients = res.data.clients || []
      return res.data
    },

    /**
     * 登录成功后的返回目标：把「来处」换成对应子系统的授权地址
     * <p>
     * 子系统没有登录页，未登录时会跳 {@code 门户/login?redirect=<它的源>}。登录完成后回到这里，
     * 由<b>服务端的子系统清单</b>比对出它是哪一个，再返回那个子系统的授权地址 ——
     * 把浏览器送过去，授权服务凭 SESSION 静默发码，子系统回调页存下令牌，用户就落在他本来要去的地方。
     * <p>
     * 之所以不直接拿 {@code redirect} 当跳转目标：那是查询串里的外部输入，直接跳就是开放重定向。
     * 这里只把它当作「源的候选值」去和已登记子系统比对，对不上就回门户工作台。
     *
     * @returns {Promise<string|null>} 子系统的授权地址；无来处或对不上时为 null
     */
    async consumeReturnTarget() {
      const origin = takeReturnOrigin()
      if (!origin) {
        return null
      }
      const wanted = originOf(origin)
      if (!wanted) {
        return null
      }
      try {
        const res = await getSubsystems()
        const matched = (res.data || []).find(
          (s) => s.ready && s.entryUrl && originOf(s.entryUrl) === wanted
        )
        // 返回后端的 302 端点，而不是带密钥的授权地址（那个地址前端根本拿不到）：
        // 浏览器跳过去后由后端拼好授权地址并 302，静默换到该子系统的令牌
        return matched ? `${API_BASE}/portal/sso-login/${matched.code}` : null
      } catch (e) {
        // 取清单失败不该让用户卡在回调页：回工作台，他能看到自己的登录状态
        return null
      }
    },

    /**
     * 只清本地凭证
     * <p>
     * 真正的登出在授权服务那边（删 SSO 会话 + 销毁浏览器 SESSION），
     * 由 utils/sso.js 的整页跳转完成；这里只负责把门户自己的令牌抹掉。
     */
    resetState() {
      clearTokens()
      this.accessToken = ''
      this.username = ''
      this.nickname = ''
      this.loginTime = ''
      this.clients = []
    }
  }
})
