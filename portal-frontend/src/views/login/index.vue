<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <h1>统一门户</h1>
        <p>一次登录，通行所有子系统</p>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="登录后凭据由授权服务统一签发"
        description="门户本身不保存账号，用户名密码只在授权服务的登录页输入一次。"
        class="tip"
      />

      <el-button
        type="primary"
        size="large"
        class="login-button"
        :loading="redirecting"
        @click="handleLogin"
      >
        {{ redirecting ? '正在前往登录…' : '统一登录' }}
      </el-button>

      <p class="hint">{{ hint }}</p>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { clearReturnOrigin, saveReturnOrigin } from '@/utils/returnTarget'

/**
 * 门户登录页 —— 同时也是各子系统的备用入口
 * <p>
 * 菜单上的「统一登录」把浏览器导航到本服务后端的 {@code GET /portal/sso-login}，
 * 由**后端**拼接授权地址并 302 过去 —— 前端不接收、也不拼接 {@code client_id} /
 * {@code client_secret}（本项目定制的授权端点要求地址上带密钥）。
 * <p>
 * 带 {@code ?redirect=} 时（有子系统把用户送过来）本页会免点击自动发起，用户不必再点一次。
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

const route = useRoute()
const redirecting = ref(false)

const returnOrigin = computed(() => {
  const raw = route.query.redirect
  return typeof raw === 'string' && raw ? raw : ''
})

const hint = computed(() => {
  if (returnOrigin.value) {
    return `正在把你送回 ${returnOrigin.value}，登录成功后会自动返回。`
  }
  return '若浏览器里还有授权服务的会话，点击后会直接进入，无需再次输入密码。'
})

onMounted(() => {
  // 记住来处（或清掉上一次残留），登录成功后的回调页会用它
  if (returnOrigin.value) {
    saveReturnOrigin(returnOrigin.value)
  } else {
    clearReturnOrigin()
  }
  // 被子系统送过来的用户直接进授权流程，不必再点一次
  if (returnOrigin.value) {
    goLogin()
  }
})

/** 交给后端 302：浏览器跟着走，最终落在授权服务的登录页 */
function goLogin() {
  redirecting.value = true
  window.location.replace(`${API_BASE}/portal/sso-login`)
}

function handleLogin() {
  goLogin()
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #12263f 0%, #1f4e79 55%, #2f7fb5 100%);
}

.login-card {
  width: 460px;
  padding: 8px 12px 20px;
  border-radius: 12px;
}

.brand {
  text-align: center;
  margin-bottom: 18px;
}

.brand h1 {
  margin: 0;
  font-size: 26px;
  letter-spacing: 4px;
  color: #1f2d3d;
}

.brand p {
  margin: 8px 0 0;
  font-size: 13px;
  color: #7a8899;
}

.tip {
  margin-bottom: 22px;
}

.login-button {
  width: 100%;
  letter-spacing: 2px;
}

.hint {
  margin: 14px 0 0;
  font-size: 12px;
  color: #9aa7b5;
  text-align: center;
  line-height: 1.6;
  word-break: break-all;
}
</style>
