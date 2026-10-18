<template>
  <div class="oauth-page">
    <el-card class="oauth-card">
      <div class="oauth-title">OAuth2 授权登录</div>

      <el-alert
        v-if="loginError"
        type="error"
        :closable="false"
        show-icon
        class="oauth-alert"
        title="用户名或密码错误"
      />

      <el-alert
        v-if="idpError"
        type="warning"
        :closable="false"
        show-icon
        class="oauth-alert"
        :title="idpError"
      />

      <p class="oauth-desc">第三方应用请求访问你的账号，请先登录本系统。</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="oauth-submit" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>

      <template v-if="enabledProviders.length > 0">
        <el-divider class="oauth-divider">或</el-divider>
        <el-button
          v-for="p in enabledProviders"
          :key="p.name"
          class="provider-submit"
          @click="loginByProvider(p.name)"
        >
          {{ p.label }}
        </el-button>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

/** 授权请求缓存（30 分钟）过期后，靠它恢复「登录后跳回授权页」 */
const RETURN_TO_KEY = 'oauth_return_to'
const returnTo = ref('')

/** 可用的外部身份提供方 + 回调错误提示 */
const providers = ref([])
const enabledProviders = computed(() => providers.value.filter((p) => p.enabled))
const IDP_ERRORS = {
  idp_disabled: '该登录方式未启用，请使用账号密码登录',
  idp_no_return: '登录请求缺少授权上下文，请从业务系统重新发起登录',
  idp_state: '登录请求已过期或已使用，请重新发起登录',
  idp_unbound: '该外部账号尚未绑定系统账号，请联系管理员',
  idp_user_disabled: '账号已停用或离职，无法登录',
  idp_failed: '外部账号登录失败，请重试或使用账号密码登录',
  // 兼容旧的企业微信错误码
  wecom_disabled: '企业微信登录未启用，请使用账号密码登录',
  wecom_no_return: '登录请求缺少授权上下文，请从业务系统重新发起登录',
  wecom_state: '登录请求已过期或已使用，请重新扫码',
  wecom_unbound: '该企业微信账号尚未绑定系统账号，请联系管理员',
  wecom_user_disabled: '账号已停用或离职，无法登录',
  wecom_failed: '企业微信登录失败，请重试或使用账号密码登录'
}
const idpError = computed(() => {
  const code = route.query.error
  return typeof code === 'string' ? IDP_ERRORS[code] || '' : ''
})

async function loadProviders() {
  try {
    const res = await fetch('/oauth/providers')
    const data = await res.json()
    providers.value = Object.entries(data || {}).map(([name, item]) => ({
      name,
      enabled: !!(item && item.enabled),
      label: (item && item.label) || name
    }))
  } catch (e) {
    providers.value = []
  }
}

function loginByProvider(provider) {
  const target = returnTo.value || sessionStorage.getItem(RETURN_TO_KEY) || ''
  window.location.href = `/oauth/idp/${provider}/authorize?returnTo=${encodeURIComponent(target)}`
}

onMounted(() => {
  const fromQuery = route.query.returnTo
  if (typeof fromQuery === 'string' && fromQuery) {
    // 存一份到 sessionStorage：登录失败会跳回 /login?error=1，查询串没了，但本次等待仍在
    sessionStorage.setItem(RETURN_TO_KEY, fromQuery)
    returnTo.value = fromQuery
  } else {
    returnTo.value = sessionStorage.getItem(RETURN_TO_KEY) || ''
  }
  loadProviders()
})

/** 授权端点登录失败时会带 error=1 跳回这里（见 OAuth2AuthorizationSecurityConfig 的 failureUrl） */
const loginError = ref(route.query.error === '1')

const formRef = ref()
const form = reactive({
  username: '',
  password: ''
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
  submitLogin()
}

/**
 * 以原生表单 POST 到授权服务器的表单登录端点。
 *
 * 这里不能用 fetch/axios：授权码与隐式模式靠浏览器跳转完成，登录成功后的响应是一个 302
 * （跳回此前被缓存的 /oauth/authorize），必须由浏览器自己跟随，令牌才换得回来。
 * 地址用相对路径，浏览器按当前源解析 —— 开发环境由 Vite 代理转给 7071，
 * 生产环境要求前端与授权服务器同源部署，Cookie 才带得上。
 */
function submitLogin() {
  const formEl = document.createElement('form')
  formEl.method = 'POST'
  formEl.action = '/oauth/login'
  const fields = { username: form.username, password: form.password }
  // 原始授权地址（相对路径）：服务端在请求缓存过期时据此跳回 /oauth/authorize
  if (returnTo.value) {
    fields.returnTo = returnTo.value
  }
  Object.entries(fields).forEach(([name, value]) => {
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

.oauth-divider {
  margin: 8px 0 16px;
  color: #909399;
}

.provider-submit {
  width: 100%;
  margin-bottom: 10px;
  color: #07c160;
  border-color: #07c160;
}

.provider-submit:hover {
  color: #ffffff;
  background-color: #07c160;
  border-color: #07c160;
}
</style>
