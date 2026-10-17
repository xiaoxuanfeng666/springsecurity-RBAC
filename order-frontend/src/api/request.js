import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from '@/utils/auth'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const service = axios.create({
  baseURL,
  timeout: 15000
})

/** 刷新请求用裸 axios 发出，避免再次进入本拦截器造成死循环 */
let refreshPromise = null

/** 并发 401 只会跳转/提示一次（2 秒内去重） */
let lastLogoutAt = 0

function redirectToLogin(message) {
  clearTokens()
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

/**
 * 用 Refresh Token 换新的 Access Token。
 * 并发 401 共用同一个 Promise，保证只刷新一次。
 */
function doRefresh() {
  if (!refreshPromise) {
    refreshPromise = axios
      .post(baseURL + '/auth/refresh', { refreshToken: getRefreshToken() })
      .then((res) => {
        const body = res.data
        if (!body || body.code !== 200) {
          // 标记为「服务端明确拒绝」，其 message 可直接展示给用户
          const err = new Error((body && body.message) || '刷新失败')
          err.fromServer = true
          throw err
        }
        setTokens({
          accessToken: body.data.accessToken,
          refreshToken: body.data.refreshToken
        })
        return body.data.accessToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

service.interceptors.request.use(
  (config) => {
    const token = getAccessToken()
    if (token) {
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

    if (response.status === 401) {
      // Access Token 过期：静默刷新后重放原请求
      const canRetry =
        config && !config._retried && !config.url.includes('/auth/refresh') && getRefreshToken()
      if (canRetry) {
        try {
          const newToken = await doRefresh()
          config._retried = true
          config.headers['Authorization'] = 'Bearer ' + newToken
          return service(config)
        } catch (e) {
          // 刷新失败（会话已失效 / 用户已离职或被停用）→ 提示原因并重新登录
          redirectToLogin(e.fromServer ? e.message : '登录状态已失效，请重新登录')
          return Promise.reject(error)
        }
      }
      redirectToLogin()
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
