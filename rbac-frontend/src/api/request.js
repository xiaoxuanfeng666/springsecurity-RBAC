import axios from 'axios'
import { ElMessage } from 'element-plus'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const service = axios.create({
  baseURL,
  timeout: 15000,
  // 登录态是服务端下发的 HttpOnly Cookie：脚本读不到它，请求时由浏览器自动携带。
  // 访问令牌、刷新令牌都在服务端，前端不再保存也不再续期。
  withCredentials: true
})

/** 并发 401 只会跳转/提示一次（2 秒内去重） */
let lastLogoutAt = 0

/**
 * 本页会话里是否已经成功请求过。
 * 首次进入（未登录）时路由守卫会请求一次 /auth/info，那是正常的探测，不该弹「登录已失效」；
 * 已经成功用过系统后再收到 401，才说明会话是在使用过程中结束的。
 */
let hasAuthenticated = false

function redirectToLogin(message) {
  const now = Date.now()
  if (now - lastLogoutAt < 2000) {
    return
  }
  lastLogoutAt = now
  if (message) {
    ElMessage.error(message)
  }
  // 跳到本系统的 /login（不是表单，是「送你去门户/授权服务登录页」的跳转页）
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    hasAuthenticated = true
    return res
  },
  (error) => {
    const { response } = error
    if (!response) {
      ElMessage.error('网络异常，请稍后重试')
      return Promise.reject(error)
    }

    if (response.status === 401) {
      // 会话已过期/被撤销：服务端令牌续期是后端静默完成的，
      // 这里还能收到 401 就说明续期也没成功，只能重新登录。
      redirectToLogin(hasAuthenticated ? '登录状态已失效，请重新登录' : null)
      return Promise.reject(error)
    }

    if (response.status === 403) {
      ElMessage.error('没有权限执行此操作')
      return Promise.reject(error)
    }
    ElMessage.error((response.data && response.data.message) || '请求失败')
    return Promise.reject(error)
  }
)

export default service
