<template>
  <div class="oauth-page">
    <el-card class="oauth-card">
      <div class="oauth-title">绑定系统账号</div>

      <el-alert
        v-if="error"
        type="error"
        :closable="false"
        show-icon
        class="oauth-alert"
        :title="error"
      />

      <p class="oauth-desc">
        该外部账号尚未绑定系统账号。请输入你的系统用户名和密码完成绑定，绑定后即可使用外部账号登录。
      </p>

      <el-form :model="form" size="large" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="系统用户名" clearable />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="系统密码" show-password clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="oauth-submit" :loading="submitting" @click="submit">
            绑定并登录
          </el-button>
        </el-form-item>
      </el-form>

      <el-button link class="back-link" @click="backToLogin">返回登录</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const ticket = computed(() =>
  typeof route.query.ticket === 'string' ? route.query.ticket : ''
)

const form = reactive({ username: '', password: '' })
const error = ref('')
const submitting = ref(false)

async function submit() {
  if (!ticket.value) {
    error.value = '绑定请求无效或已过期，请重新发起登录'
    return
  }
  if (!form.username || !form.password) {
    error.value = '请输入系统用户名和密码'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    const res = await fetch('/oauth/idp/bind', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ticket: ticket.value, ...form })
    })
    const body = await res.json()
    if (!body || body.code !== 200) {
      error.value = (body && body.message) || '绑定失败，请重试'
      return
    }
    // 登录态 Cookie 已在响应中写入，直接回到最初的授权请求
    window.location.href = body.data.returnTo || '/login'
  } catch (e) {
    error.value = '网络异常，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function backToLogin() {
  router.replace('/login')
}
</script>

<style scoped>
.oauth-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a5f 0%, #2c5f8a 100%);
}

.oauth-card {
  width: 460px;
  padding: 8px 12px;
}

.oauth-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 16px;
}

.oauth-alert {
  margin-bottom: 16px;
}

.oauth-desc {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.7;
  color: #606266;
}

.oauth-submit {
  width: 100%;
}

.back-link {
  display: block;
  margin: 0 auto;
}
</style>
