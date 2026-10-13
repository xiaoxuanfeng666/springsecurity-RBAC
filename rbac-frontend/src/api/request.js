import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from '@/utils/auth'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const service = axios.create({
  baseURL,
  timeout: 15000
})

// 刷新中的标记与等待队列：保证并发 401 只触发一次刷新
let isRefreshing = false
let pendingQueue = []

function redirectToLogin() {
  clearTokens()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

function flushQueue(token) {
  pendingQueue.forEach((cb) => cb(token))
  pendingQueue = []
}

/**
 * 处理 401：用 refreshToken 换取新 Access，成功后重放原请求
 */
async function handleUnauthorized(config) {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    redirectToLogin()
    return Promise.reject(new Error('未登录'))
  }

  // 已有刷新请求在途：排队等待，拿到新 token 后重放
  if (isRefreshing) {
    return new Promise((resolve) => {
      pendingQueue.push((token) => {
        config._retry = true
        config.headers['Authorization'] = 'Bearer ' + token
        resolve(service(config))
      })
    })
  }

  isRefreshing = true
  try {
    // 使用裸 axios，避免再次进入拦截器造成递归
    const { data } = await axios.post(baseURL + '/auth/refresh', { refreshToken })
    if (data.code !== 200) {
      throw new Error(data.message || '刷新失败')
    }
    const { accessToken, refreshToken: newRefreshToken } = data.data
    setTokens(accessToken, newRefreshToken)
    flushQueue(accessToken)

    config._retry = true
    config.headers['Authorization'] = 'Bearer ' + accessToken
    return service(config)
  } catch (e) {
    pendingQueue = []
    redirectToLogin()
    return Promise.reject(e)
  } finally {
    isRefreshing = false
  }
}

service.interceptors.request.use(
  (config) => {
    const token = getAccessToken()
    if (token && !config.url.includes('/auth/refresh')) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  async (error) => {
    const { response, config } = error
    if (!response) {
      ElMessage.error('网络异常，请稍后重试')
      return Promise.reject(error)
    }
    if (response.status === 401 && !config._retry) {
      return handleUnauthorized(config)
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
