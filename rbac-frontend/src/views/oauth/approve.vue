<template>
  <div class="approve-page">
    <el-card class="approve-card">
      <div class="approve-title">授权确认</div>

      <template v-if="loadError">
        <el-alert type="error" :closable="false" show-icon :title="loadError" />
        <p class="approve-hint">
          本页由授权端点跳转进入，<b>不能直接打开</b>。请先启动后端，再从授权地址发起：
        </p>
        <pre class="approve-url">/oauth/authorize?response_type=code&amp;client_id=...&amp;client_secret=...
  &amp;redirect_uri=...&amp;scope=read%20write&amp;state=abc</pre>
      </template>

      <template v-else>
        <p class="approve-desc">
          应用 <span class="client-id">{{ info.clientId }}</span> 请求访问你的账号，
          将获得以下权限：
        </p>

        <ul class="scope-list">
          <li v-for="scope in info.scopes" :key="scope">{{ scope }}</li>
        </ul>

        <div class="approve-actions">
          <el-button @click="submit(false)">拒 绝</el-button>
          <el-button type="primary" :loading="submitting" @click="submit(true)">同意授权</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import request from '@/api/request'

const info = reactive({ clientId: '', scopes: [] })
const loadError = ref('')
const submitting = ref(false)

onMounted(async () => {
  try {
    // 待授权的请求存在服务端会话里，前端拿不到，故由后端接口读出来
    const res = await request.get('/oauth/approval-info')
    Object.assign(info, res.data)
  } catch (e) {
    // 失败原因可能有好几种：后端没启动、直接打开本页（会话里没有待授权请求）、
    // 或授权流程已结束。这里不猜具体是哪种 —— 服务端若给了文案，拦截器已弹提示，
    // 页面只负责给出可操作的下一步。
    loadError.value = '未能获取待授权信息（后端未启动，或当前没有进行中的授权请求）'
  }
})

/**
 * 提交授权结果。
 *
 * 只提交 user_oauth_approval 一个标志 —— 本项目实际生效的审批处理器是
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
 * 与登录一致：这一步靠浏览器跳转，带不上 JWT，只能走表单提交；
 * 地址用相对路径，浏览器按当前源解析，经前端代理到后端。
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
.approve-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #1f3a5f 0%, #2b5876 50%, #4e4376 100%);
}

.approve-card {
  width: 440px;
  padding: 12px 8px;
}

.approve-title {
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 20px;
  color: #303133;
}

.approve-desc {
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

.approve-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.approve-hint {
  margin: 16px 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}

.approve-url {
  margin: 0;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: #909399;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
