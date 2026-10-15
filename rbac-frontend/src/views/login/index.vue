<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">RBAC 权限管理后台</div>
      <el-alert
        v-if="oauthMode"
        type="info"
        :closable="false"
        show-icon
        class="oauth-alert"
        title="第三方应用请求授权"
        description="请先登录本系统，登录后将返回授权页面"
      />
      <el-alert
        v-if="oauthError"
        type="error"
        :closable="false"
        show-icon
        class="oauth-alert"
        title="用户名或密码错误"
      />
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-button" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tip">
        <div>测试账号：</div>
        <div>超级管理员 admin / admin123</div>
        <div>普通用户 zhangsan / admin123</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 后端授权端点发现未登录时会带 oauth2=1 跳到本页（见 OAuth2LoginEntryPoint）
const oauthMode = computed(() => route.query.oauth2 === '1')
const oauthError = computed(() => route.query.error === '1')

const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: 'admin123'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  if (oauthMode.value) {
    submitOAuthLogin()
    return
  }

  loading.value = true
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    await router.push('/')
  } catch (e) {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/**
 * OAuth2 授权流程的表单登录。
 *
 * 与上面的 SPA 登录不同：授权码/隐式模式靠浏览器跳转，带不上 JWT，
 * 因此登录态必须落在后端的 HttpSession 里 —— 走 Spring Security 的表单登录端点
 * /oauth/login。登录成功后由后端跳回此前被缓存的 /oauth/authorize。
 */
function submitOAuthLogin() {
  loading.value = true
  const formEl = document.createElement('form')
  formEl.method = 'POST'
  formEl.action = '/oauth/login'
  Object.entries({ username: form.username, password: form.password }).forEach(([name, value]) => {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = name
    input.value = value
    formEl.appendChild(input)
  })
  document.body.appendChild(formEl)
  formEl.submit()
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #1f3a5f 0%, #2b5876 50%, #4e4376 100%);
}

.login-card {
  width: 400px;
  padding: 12px 8px;
}

.login-title {
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 24px;
  color: #303133;
}

.login-button {
  width: 100%;
}

.oauth-alert {
  margin-bottom: 16px;
}

.login-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.8;
  text-align: center;
}
</style>
