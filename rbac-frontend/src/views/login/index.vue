<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">RBAC 权限管理后台</div>
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
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

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

.login-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.8;
  text-align: center;
}
</style>
