import { useUserStore } from '@/store/user'

/**
 * 按钮级权限指令
 * 用法：v-permission="'system:user:add'" 或 v-permission="['system:user:add', 'system:user:edit']"
 */
export default {
  mounted(el, binding) {
    const { value } = binding
    if (!value) {
      return
    }
    const userStore = useUserStore()
    const perms = Array.isArray(value) ? value : [value]
    if (!userStore.hasAnyPerm(perms)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
