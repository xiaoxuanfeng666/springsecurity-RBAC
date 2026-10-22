import axios from 'axios'
import { ElMessageBox } from 'element-plus'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

/** 并发多个敏感请求时只弹一次密码框 */
let pending = null

/**
 * 敏感操作二次验证：弹窗要求重新输入登录密码，验证通过后原请求会被重放。
 * 用裸 axios 发请求，避免再次进入业务拦截器造成循环。
 */
export function requestElevation() {
  if (!pending) {
    pending = (async () => {
      const { value } = await ElMessageBox.prompt(
        '该操作需要重新验证登录密码',
        '安全验证',
        {
          inputType: 'password',
          inputPlaceholder: '请输入登录密码',
          confirmButtonText: '验证',
          cancelButtonText: '取消',
          inputValidator: (v) => (v ? true : '请输入密码')
        }
      )
      const res = await axios.post(
        baseURL + '/auth/elevate',
        { password: value },
        { withCredentials: true }
      )
      if (!res.data || res.data.code !== 200) {
        throw new Error((res.data && res.data.message) || '密码验证未通过')
      }
    })().finally(() => {
      pending = null
    })
  }
  return pending
}
