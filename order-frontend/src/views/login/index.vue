<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <h2>订单中心</h2>
        <p>登录授权由 RBAC 授权服务器统一提供</p>
      </div>

      <el-tabs v-model="activeTab">
        <!-- 密码模式：账号密码交给 rbac-oauth 校验 -->
        <el-tab-pane label="账号密码登录" name="password">
          <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                placeholder="密码"
                :prefix-icon="Lock"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="submit" :loading="loading" @click="handleLogin">
                登录
              </el-button>
            </el-form-item>
          </el-form>
          <div class="tip">账号为 RBAC 系统中的用户，本系统不保存密码。</div>
        </el-tab-pane>

        <!-- 授权码模式：跳去 rbac-oauth 的授权页 -->
        <el-tab-pane label="授权码登录" name="code">
          <div class="code-panel">
            <p class="code-desc">
              跳转到 RBAC 授权服务器完成登录与授权，授权成功后自动回到本系统。
            </p>
            <el-button type="primary" class="submit" :loading="redirecting" @click="handleAuthorizeLogin">
              前往授权
            </el-button>
            <div class="tip">回调地址：{{ redirectUri }}</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getAuthorizeUrl } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('password')
const loading = ref(false)
const redirecting = ref(false)
const formRef = ref()
const form = reactive({ username: 'admin', password: 'admin123' })
const redirectUri = 'http://localhost:5176/oauth/callback'

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
  loading.value = true
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}

/**
 * 授权码模式：拿后端拼好的授权地址整页跳过去。
 * state 用于回来时校验是同一次授权请求发起的。
 */
async function handleAuthorizeLogin() {
  redirecting.value = true
  try {
    const state = Math.random().toString(36).slice(2, 10)
    sessionStorage.setItem('order_oauth_state', state)
    const res = await getAuthorizeUrl(state)
    window.location.href = res.data
  } finally {
    redirecting.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a5f 0%, #2c5f8a 100%);
}

.login-card {
  width: 420px;
  padding: 8px 12px;
}

.brand {
  text-align: center;
  margin-bottom: 8px;
}

.brand h2 {
  margin: 0 0 6px;
  color: #303133;
}

.brand p {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.submit {
  width: 100%;
}

.code-panel {
  padding: 4px 0;
}

.code-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  margin: 0 0 12px;
}

.tip {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  text-align: center;
  word-break: break-all;
}
</style>
