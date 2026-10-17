<template>
  <div class="jump-page">
    <el-card class="jump-card" shadow="always">
      <el-result
        icon="info"
        title="正在跳转到登录页"
        sub-title="本系统没有登录页，正带你去统一登录…"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAccessToken } from '@/utils/auth'

/**
 * 登录入口：把浏览器导航到本系统后端的 {@code /auth/sso-login}
 * <p>
 * 授权地址由**后端**拼接，并由后端直接发 302 —— 前端不接收、也不拼接
 * {@code client_id} / {@code client_secret}。本项目定制的授权端点要求地址上带密钥，
 * 一旦把它作为接口数据返回给前端，密钥就会出现在前端代码、浏览器网络面板与前端日志里。
 * <p>
 * 跳转后的落点：
 * <pre>
 *   未登录：/api/auth/sso-login → 302 → 5174/oauth/authorize → 302 → http://localhost:5174/login
 *   已登录：/api/auth/sso-login → 302 → 5174/oauth/authorize → 凭会话静默发码 → 回调换令牌 → 落地
 * </pre>
 * 第二种情况登录页根本不出现 —— 「登录一次，进第二个子系统免登录」靠的就是授权服务的会话。
 */
const router = useRouter()
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

onMounted(() => {
  // 已登录（例如用户手敲 /login）就直接回首页，没必要再走一趟授权
  if (getAccessToken()) {
    router.replace('/')
    return
  }
  window.location.replace(`${API_BASE}/auth/sso-login`)
})
</script>

<style scoped>
.jump-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a5f 0%, #2c5f8a 100%);
}

.jump-card {
  width: 460px;
  padding: 8px 12px;
}
</style>
