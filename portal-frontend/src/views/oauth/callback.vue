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
 * 授权码回调页
 * <p>
 * 授权服务在用户通过认证后带 ?code=xxx 跳回这里。code 交给门户后端换成令牌
 * （换令牌要带 client_secret，不能放前端做），随后进入工作台。
 */
const router = useRouter()
const userStore = useUserStore()

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
  if (!code) {
    status.value = 'error'
    title.value = '缺少授权码'
    message.value = '回调地址上没有 code 参数，请重新发起登录'
    return
  }

  try {
    await userStore.loginByCode(code)

    // 用户可能是从某个子系统被送过来的（那个子系统没有登录页）。
    // 拿到门户令牌后立刻把他送回去：门户已能访问子系统清单，据此换出该子系统的授权地址，
    // 浏览器跳过去时授权服务凭 SESSION 直接发码，子系统回调页存下令牌，用户就落在原来要去的地方。
    const returnTarget = await userStore.consumeReturnTarget()
    if (returnTarget) {
      status.value = 'success'
      title.value = '登录成功'
      message.value = '正在返回你刚才访问的系统…'
      window.location.replace(returnTarget)
      return
    }

    status.value = 'success'
    title.value = '登录成功'
    message.value = '正在进入统一门户…'
    // 清掉地址栏上的 code，避免刷新页面时拿旧码再换一次（授权码是一次性的）
    window.history.replaceState({}, '', '/oauth/callback')
    router.replace('/')
  } catch (e) {
    status.value = 'error'
    title.value = '登录失败'
    message.value = e.message || '授权码无效或已过期，请重新登录'
  }
})
</script>

<style scoped>
.callback-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #12263f 0%, #1f4e79 55%, #2f7fb5 100%);
}

.callback-card {
  width: 460px;
  border-radius: 12px;
}
</style>
