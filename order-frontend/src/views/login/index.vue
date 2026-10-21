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

/**
 * 登录入口：把浏览器导航到本系统后端的 {@code /auth/sso-login}
 * <p>
 * 授权地址由**后端**拼接，并由后端直接发 302 —— 前端不接收、也不拼接
 * {@code client_id} / {@code redirect_uri} / {@code state}。客户端密钥只在后端
 * 调令牌端点时使用，不会出现在任何浏览器可见的地址里。
 * <p>
 * 跳转后的落点：
 * <pre>
 *   未登录：/api/auth/sso-login → 302 → 5174/oauth/authorize → 302 → http://localhost:5174/login
 *   已登录：/api/auth/sso-login → 302 → 5174/oauth/authorize → 凭会话静默发码 → 回调建立会话 → 落地
 * </pre>
 * 第二种情况登录页根本不出现 —— 「登录一次，进第二个子系统免登录」靠的就是授权服务的会话。
 * 登录态在 HttpOnly Cookie 里，前端读不到，所以这里不做「是否已登录」的判断，
 * 统一交给授权服务：已登录时它会直接静默发码。
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

onMounted(() => {
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
