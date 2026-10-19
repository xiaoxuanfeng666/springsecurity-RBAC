<template>
  <div class="oauth-page">
    <el-card class="oauth-card oauth-card--login">
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

      <!-- 左右两栏：左侧企业微信扫码，右侧账号密码 -->
      <div class="login-columns" :class="{ 'single-col': !qrEnabled }">
        <section v-if="qrEnabled" class="login-col qr-col">
          <div class="col-title">企业微信扫码登录</div>
          <div class="qr-panel">
            <div class="qr-box">
              <img
                v-if="qrState === 'PENDING' && qrDataUrl"
                :src="qrDataUrl"
                class="qr-image"
                alt="企业微信登录二维码"
              />
              <div v-else class="qr-placeholder">
                <template v-if="qrState === 'LOADING'">二维码生成中...</template>
                <template v-else-if="qrState === 'CONFIRMED'">扫码成功，正在登录...</template>
                <template v-else-if="qrState === 'FAILED'">{{ qrMessage || '二维码不可用' }}</template>
                <template v-else>二维码已过期</template>
                <el-button
                  v-if="qrState === 'EXPIRED' || qrState === 'FAILED'"
                  link
                  type="primary"
                  class="qr-refresh"
                  @click="refreshQr"
                >
                  刷新二维码
                </el-button>
              </div>
            </div>
            <div v-if="qrState === 'PENDING'" class="qr-tip">
              请使用企业微信扫一扫，{{ qrSeconds }} 秒后失效
            </div>
            <div v-else-if="qrState === 'CONFIRMED'" class="qr-tip success">
              扫码成功，正在为您建立登录状态...
            </div>
          </div>
        </section>

        <div v-if="qrEnabled" class="login-divider"></div>

        <section class="login-col password-col">
          <div class="col-title">账号密码登录</div>
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
        </section>
      </div>

      <template v-if="otherProviders.length > 0">
        <el-divider class="oauth-divider">或</el-divider>
        <el-button
          v-for="p in otherProviders"
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
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import QRCode from 'qrcode'

const route = useRoute()

/** 授权请求缓存（30 分钟）过期后，靠它恢复「登录后跳回授权页」 */
const RETURN_TO_KEY = 'oauth_return_to'
const returnTo = ref('')

/** 可用的外部身份提供方 + 回调错误提示 */
const providers = ref([])
const enabledProviders = computed(() => providers.value.filter((p) => p.enabled))
const wecomEnabled = computed(() => enabledProviders.value.some((p) => p.name === 'wecom'))
/** 有内嵌二维码时，不再重复渲染企业微信跳转按钮 */
const otherProviders = computed(() =>
  enabledProviders.value.filter((p) => !(qrEnabled.value && p.name === 'wecom'))
)

/** ---------------- 企业微信扫码登录（短链 + 轮询） ---------------- */
const qrState = ref('IDLE') // IDLE / LOADING / PENDING / CONFIRMED / EXPIRED / FAILED
const qrDataUrl = ref('')
const qrSeconds = ref(0)
const qrMessage = ref('')
let qrCountdownTimer = null
let qrPollTimer = null

const qrEnabled = computed(
  () => wecomEnabled.value && !!currentReturnTo() && qrState.value !== 'IDLE'
)

function currentReturnTo() {
  return returnTo.value || sessionStorage.getItem(RETURN_TO_KEY) || ''
}

async function refreshQr() {
  stopQrTimers()
  qrState.value = 'LOADING'
  qrDataUrl.value = ''
  qrMessage.value = ''
  try {
    const res = await fetch('/oauth/wecom/qr', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ returnTo: currentReturnTo() })
    })
    const body = await res.json()
    if (body.code !== 200 || !body.data) {
      qrState.value = 'FAILED'
      qrMessage.value = body.message || '二维码生成失败'
      return
    }
    qrDataUrl.value = await QRCode.toDataURL(body.data.shortUrl, {
      width: 220,
      margin: 1,
      color: { dark: '#000000', light: '#ffffff' }
    })
    qrSeconds.value = body.data.expiresIn || 300
    qrState.value = 'PENDING'
    startQrTimers()
  } catch (e) {
    qrState.value = 'FAILED'
    qrMessage.value = '二维码生成失败，请检查网络'
  }
}

function startQrTimers() {
  stopQrTimers()
  const expireAt = Date.now() + qrSeconds.value * 1000
  qrCountdownTimer = setInterval(() => {
    qrSeconds.value = Math.max(0, Math.ceil((expireAt - Date.now()) / 1000))
    if (qrSeconds.value <= 0) {
      markQrExpired()
    }
  }, 1000)
  qrPollTimer = setInterval(pollQrStatus, 2000)
}

function stopQrTimers() {
  if (qrCountdownTimer) {
    clearInterval(qrCountdownTimer)
    qrCountdownTimer = null
  }
  if (qrPollTimer) {
    clearInterval(qrPollTimer)
    qrPollTimer = null
  }
}

function markQrExpired() {
  stopQrTimers()
  qrSeconds.value = 0
  if (qrState.value === 'PENDING') {
    qrState.value = 'EXPIRED'
  }
}

async function pollQrStatus() {
  if (qrState.value !== 'PENDING' && qrState.value !== 'CONFIRMED') {
    return
  }
  try {
    const res = await fetch('/oauth/wecom/qr/status', { credentials: 'include' })
    const body = await res.json()
    if (body.code !== 200 || !body.data) {
      return
    }
    const data = body.data
    if (data.status === 'CONFIRMED') {
      if (qrState.value !== 'CONFIRMED') {
        qrState.value = 'CONFIRMED'
        stopQrTimers()
      }
      await completeQr(data.loginCode)
    } else if (data.status === 'EXPIRED' || data.status === 'NO_TICKET') {
      markQrExpired()
    } else if (data.status === 'FAILED') {
      stopQrTimers()
      qrState.value = 'FAILED'
      qrMessage.value = data.message || '扫码确认失败'
    }
  } catch (e) {
    // 网络抖动时继续轮询
  }
}

async function completeQr(loginCode) {
  if (!loginCode) {
    return
  }
  try {
    const res = await fetch('/oauth/wecom/qr/complete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ loginCode })
    })
    const body = await res.json()
    if (body.code === 200 && body.data) {
      window.location.href = body.data.returnTo || '/'
    } else {
      stopQrTimers()
      qrState.value = 'FAILED'
      qrMessage.value = body.message || '登录失败，请刷新二维码'
    }
  } catch (e) {
    stopQrTimers()
    qrState.value = 'FAILED'
    qrMessage.value = '登录失败，请刷新二维码'
  }
}

function maybeInitQr() {
  if (currentReturnTo() && wecomEnabled.value) {
    refreshQr()
  }
}
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

onMounted(async () => {
  const fromQuery = route.query.returnTo
  if (typeof fromQuery === 'string' && fromQuery) {
    // 存一份到 sessionStorage：登录失败会跳回 /login?error=1，查询串没了，但本次等待仍在
    sessionStorage.setItem(RETURN_TO_KEY, fromQuery)
    returnTo.value = fromQuery
  } else {
    returnTo.value = sessionStorage.getItem(RETURN_TO_KEY) || ''
  }
  await loadProviders()
  maybeInitQr()
})

onBeforeUnmount(stopQrTimers)

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
  text-align: center;
}

.oauth-submit {
  width: 100%;
}

.oauth-card--login {
  width: 820px;
  max-width: calc(100vw - 24px);
}

.login-columns {
  display: flex;
  align-items: flex-start;
  gap: 32px;
}

.login-col {
  flex: 1;
  min-width: 0;
}

.login-divider {
  width: 1px;
  align-self: stretch;
  background: #ebeef5;
}

.col-title {
  margin-bottom: 16px;
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.login-columns.single-col .password-col {
  max-width: 340px;
  margin: 0 auto;
}

.qr-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0;
}

@media (max-width: 760px) {
  .login-columns {
    flex-direction: column;
    gap: 20px;
  }

  .login-divider {
    width: 100%;
    height: 1px;
  }
}

.qr-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 220px;
  height: 220px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.qr-image {
  width: 220px;
  height: 220px;
}

.qr-placeholder {
  padding: 0 16px;
  font-size: 13px;
  line-height: 1.8;
  color: #909399;
  text-align: center;
}

.qr-refresh {
  display: block;
  margin: 6px auto 0;
}

.qr-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.qr-tip.success {
  color: #07c160;
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
