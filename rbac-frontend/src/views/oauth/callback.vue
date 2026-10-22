<template>
  <div class="callback-page">
    <el-card class="callback-card" shadow="always">
      <el-result
        :icon="status === 'success' ? 'success' : status === 'error' ? 'error' : 'info'"
        :title="title"
        :sub-title="message"
      >
        <template #extra>
          <el-button v-if="status === 'error'" type="primary" @click="backToLogin">
            返回登录
          </el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

/**
 * 授权码模式的回调页
 * <p>
 * rbac-oauth 在用户认证/确认授权后会带 ?code=xxx 跳回这里，
 * 本页负责把 code 交给后端换令牌（换令牌要带 client_secret，不能放前端做）。
 * 若用户拒绝授权，则回来的是 ?error=access_denied。
 */
const router = useRouter()
const userStore = useUserStore()

/** 子系统 state 过期/已使用：子系统返回 2020，管理后台返回 1032 */
const STATE_INVALID = 1032
const RETRY_KEY = 'oauth_callback_retry'

const status = ref('loading')
const title = ref('正在登录')
const message = ref('正在用授权码换取令牌，请稍候…')

function backToLogin() {
  router.replace('/login')
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const error = params.get('error')
  if (error) {
    status.value = 'error'
    title.value = '授权未通过'
    message.value = params.get('error_description') || '用户取消或拒绝了授权'
    return
  }

  const code = params.get('code')
  const state = params.get('state')
  if (!code || !state) {
    status.value = 'error'
    title.value = '缺少授权参数'
    message.value = '回调地址上没有 code 或 state 参数，请重新发起授权'
    return
  }

  try {
    await userStore.loginByCode(code, state)
    sessionStorage.removeItem(RETRY_KEY)
    status.value = 'success'
    title.value = '登录成功'
    message.value = '正在进入 RBAC 管理后台…'
    // 清掉地址栏上的 code，避免刷新页面时拿旧码再换一次（授权码是一次性的）
    window.history.replaceState({}, '', '/oauth/callback')
    router.replace('/')
  } catch (e) {
    // state 过期（在登录页停留过久、扫码完成得太晚）：自动重新发起一次授权。
    // 此时浏览器通常已有授权服务登录态，第二遍是静默发码，用户无感。
    if (e.code === STATE_INVALID && !sessionStorage.getItem(RETRY_KEY)) {
      sessionStorage.setItem(RETRY_KEY, '1')
      window.location.replace('/login')
      return
    }
    sessionStorage.removeItem(RETRY_KEY)
    status.value = 'error'
    title.value = '登录失败'
    message.value = e.message || '授权码无效或已过期，请重新发起授权'
  }
})
</script>

<style scoped>
.callback-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a5f 0%, #2b5876 50%, #4e4376 100%);
}

.callback-card {
  width: 460px;
}
</style>
