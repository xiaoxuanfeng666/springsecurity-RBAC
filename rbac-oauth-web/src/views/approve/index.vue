<template>
  <div class="oauth-page">
    <el-card class="oauth-card oauth-card--wide">
      <div class="oauth-title">授权确认</div>

      <template v-if="loadError">
        <el-alert type="error" :closable="false" show-icon :title="loadError" />
        <p class="oauth-hint">
          本页由授权端点跳转进入，<b>不能直接打开</b>。请先启动授权服务器（rbac-oauth，默认 7071），
          再从授权地址发起：
        </p>
        <pre class="oauth-pre">/oauth/authorize?response_type=code&amp;client_id=...&amp;client_secret=...
  &amp;redirect_uri=...&amp;scope=read%20write&amp;state=abc</pre>
      </template>

      <template v-else>
        <p class="oauth-desc">
          应用 <span class="client-id">{{ info.clientId }}</span> 请求访问你的账号，
          将获得以下权限：
        </p>

        <ul class="scope-list">
          <li v-for="scope in info.scopes" :key="scope">{{ scope }}</li>
        </ul>

        <div class="oauth-actions">
          <el-button @click="submit(false)">拒 绝</el-button>
          <el-button type="primary" :loading="submitting" @click="submit(true)">同意授权</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'

const info = reactive({ clientId: '', scopes: [] })
const loadError = ref('')
const submitting = ref(false)

onMounted(async () => {
  try {
    // 待授权的请求存在授权服务器的 HTTP 会话里（AuthorizationEndpoint 以 @SessionAttributes 写入），
    // 本页拿不到会话，故由后端接口读出来。credentials 必须带上会话 Cookie，否则读不到。
    const response = await fetch('/oauth/approval-info', { credentials: 'same-origin' })
    const body = await response.json()
    if (!body || body.code !== 200) {
      loadError.value = (body && body.message) || '未能获取待授权信息'
      return
    }
    Object.assign(info, body.data)
  } catch (e) {
    loadError.value = '未能获取待授权信息（授权服务器未启动，或当前没有进行中的授权请求）'
  }
})

/**
 * 提交授权结果。
 *
 * 只提交 user_oauth_approval 一个标志 —— 实际生效的审批处理器是
 * TokenStoreUserApprovalHandler，它对逐 scope 的审批参数（scope.xxx=true/false）
 * 一律忽略且不裁剪 scope，所以这里不做逐 scope 勾选，免得给出无效的交互。
 * 若将来换成 ApprovalStoreUserApprovalHandler + 审批存储，才需要按
 * `scope.<名称>` 的形式逐项提交。
 */
function submit(approved) {
  submitting.value = true
  postToAuthorize({ user_oauth_approval: String(approved) })
}

/**
 * 以原生表单 POST 回授权端点。
 *
 * 与登录一致：这一步靠浏览器跳转完成（成功后 302 带 code 回第三方回调地址），
 * 地址用相对路径，浏览器按当前源解析，开发环境经 Vite 代理转给 rbac-oauth。
 */
function postToAuthorize(params) {
  const formEl = document.createElement('form')
  formEl.method = 'POST'
  formEl.action = '/oauth/authorize'
  Object.entries(params).forEach(([name, value]) => {
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
.oauth-desc {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.7;
  color: #606266;
}

.client-id {
  font-weight: 600;
  color: #303133;
}

.scope-list {
  margin: 0 0 20px;
  padding: 12px 16px 12px 32px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 14px;
  color: #303133;
  line-height: 1.9;
}

.oauth-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
