import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getAccessToken, clearTokens } from '@/utils/auth'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000
})

request.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截：只处理登录态失效
 * <p>
 * 门户令牌有效期 1 小时（clientdetails.access_token_validity=3600）。过期或用户在别处
 * 统一登出之后，授权服务的 check_token 会拒绝它，这里就把本地令牌清掉、回登录页。
 * 回到登录页后点「统一登录」即可 —— 若授权服务的 SESSION 还在，会一路静默地重新拿到令牌。
 */
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code !== undefined && body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      clearTokens()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
