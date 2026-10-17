<template>
  <div class="portal">
    <header class="topbar">
      <div class="topbar-left">
        <span class="logo">统一门户</span>
        <span class="divider" />
        <span class="subtitle">一次登录，通行所有子系统</span>
      </div>
      <div class="topbar-right">
        <span class="user">
          {{ userStore.nickname || userStore.username || '未登录' }}
          <span v-if="userStore.loginTime" class="login-time">（{{ userStore.loginTime }} 登录）</span>
        </span>
        <el-button type="danger" plain size="small" @click="handleLogout">
          统一登出
        </el-button>
      </div>
    </header>

    <main class="content">
      <el-card shadow="never" class="section">
        <div class="section-head">
          <div>
            <h2>我的子系统</h2>
            <p class="section-desc">
              「已签发令牌」表示本次登录已经为该子系统换好令牌，直接进入即可，不会再要求登录。
            </p>
          </div>
          <el-button
            type="primary"
            :loading="running"
            :disabled="!pendingCount"
            @click="loginAll"
          >
            一键登录所有子系统
            <template v-if="pendingCount">（剩余 {{ pendingCount }} 个）</template>
          </el-button>
        </div>

        <el-alert
          v-if="chainMessage"
          :type="chainMessageType"
          :closable="false"
          show-icon
          :title="chainMessage"
          class="chain-alert"
        />

        <el-empty v-if="!loading && !subsystems.length" description="还没有登记任何子系统" />

        <div v-else class="cards">
          <el-card
            v-for="sys in subsystems"
            :key="sys.code"
            shadow="hover"
            class="subsystem-card"
            :class="{ 'is-offline': !sys.ready }"
          >
            <div class="card-head">
              <span class="card-title">{{ sys.name }}</span>
              <el-tag v-if="!sys.ready" type="info" size="small">不可用</el-tag>
              <el-tag v-else-if="userStore.loggedInTo(sys.clientId)" type="success" size="small">
                已签发令牌
              </el-tag>
              <el-tag v-else type="warning" size="small">未登录</el-tag>
            </div>

            <p class="card-desc">{{ sys.ready ? `客户端 ${sys.clientId}` : sys.unavailableReason }}</p>

            <div class="card-actions">
              <el-button
                type="primary"
                size="small"
                :disabled="!sys.ready"
                @click="enter(sys)"
              >
                进入
              </el-button>
            </div>
          </el-card>
        </div>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSubsystems } from '@/api/portal'
import { useUserStore } from '@/store/user'
import { ssoLogout } from '@/utils/sso'
import { startSessionWatch, stopSessionWatch } from '@/utils/sessionWatch'

/** 接口前缀：iframe 要指向后端的 302 端点，用的就是这个前缀 */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

/** 轮询间隔：够快，又不至于把后端打爆（每一步都只是一次 Redis 读） */
const POLL_INTERVAL = 400

/** 单个子系统的等待上限：超时就认输，把这一步交还给用户手动点 */
const STEP_TIMEOUT = 15000

const userStore = useUserStore()

const subsystems = ref([])
const loading = ref(true)
const running = ref(false)
const chainMessage = ref('')
const chainMessageType = ref('info')

const pendingCount = computed(
  () => subsystems.value.filter((s) => s.ready && !userStore.loggedInTo(s.clientId)).length
)

onMounted(() => {
  load()
  // 被管理员踢下线、或本人在别处登出后，切回这个标签页就会立刻发现并跳回登录页
  startSessionWatch()
})
onUnmounted(stopSessionWatch)

async function load() {
  try {
    const [subsystemRes] = await Promise.all([getSubsystems(), userStore.loadInfo()])
    subsystems.value = subsystemRes.data || []
  } catch (e) {
    // 拦截器已经提示过了
  } finally {
    loading.value = false
  }
}

/**
 * 一键登录所有子系统
 * <p>
 * 每个子系统在隐藏 iframe 里跑一遍授权码流程：浏览器已经持有授权服务的 SESSION，
 * 且这些客户端都配了 autoApproveScopes，所以整个流程是静默的 —— 不弹登录页、不弹确认页，
 * 子系统自己的回调页收到 code 后换好令牌并存进它自己源的 localStorage。
 * <p>
 * 进度不靠「猜 iframe 加载完没有」，而是轮询 /portal/info：授权服务在签发令牌时会把客户端
 * 记进 SSO 会话，因此「clients 里出现了这个子系统」才算这一步真的成了。
 */
async function loginAll() {
  const pending = subsystems.value.filter((s) => s.ready && !userStore.loggedInTo(s.clientId))
  if (!pending.length) {
    return
  }
  running.value = true
  chainMessage.value = ''
  const failed = []

  for (const sys of pending) {
    chainMessageType.value = 'info'
    chainMessage.value = `正在为「${sys.name}」换取令牌…`
    const ok = await launchInIframe(sys)
    if (!ok) {
      failed.push(sys.name)
    }
  }

  running.value = false
  if (failed.length) {
    chainMessageType.value = 'warning'
    chainMessage.value = `「${failed.join('、')}」未能自动完成，请点对应卡片的「进入」手动登录。`
  } else {
    chainMessageType.value = 'success'
    chainMessage.value = '已为所有子系统签发令牌，直接进入即可。'
  }
}

/**
 * 在隐藏 iframe 里跑完一个子系统的授权码流程，成功返回 true
 * <p>
 * 不读取 iframe 里的任何内容（跨源也读不到）：只要 SSO 会话里出现了这个客户端，
 * 就说明子系统那边确实把令牌拿到手了。
 */
function launchInIframe(sys) {
  return new Promise((resolve) => {
    const frame = document.createElement('iframe')
    frame.style.display = 'none'
    // iframe 指向后端的 302 端点：由后端拼好带密钥的授权地址并 302 过去，
    // 前端（包括这一层 JS）拿不到 client_secret
    frame.src = `${API_BASE}/portal/sso-login/${sys.code}`
    document.body.appendChild(frame)

    const startedAt = Date.now()
    const timer = setInterval(async () => {
      try {
        await userStore.loadInfo()
        if (userStore.loggedInTo(sys.clientId)) {
          finish(true)
          return
        }
      } catch (e) {
        // 单次轮询失败不中断：401 会由拦截器处理，这里继续等
      }
      if (Date.now() - startedAt > STEP_TIMEOUT) {
        finish(false)
      }
    }, POLL_INTERVAL)

    function finish(ok) {
      clearInterval(timer)
      frame.remove()
      resolve(ok)
    }
  })
}

function enter(sys) {
  window.open(sys.entryUrl, '_blank')
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm(
      '统一登出会立即失效你在所有子系统里的登录状态，确定吗？',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  // 统一登出：先带令牌让授权服务删掉 SSO 会话（所有子系统的令牌立刻失效），
  // 再整页跳过去销毁浏览器会话，最后回到门户登录页。跳转成功时不会返回。
  const navigated = await ssoLogout()
  userStore.resetState()
  if (!navigated) {
    ElMessage.warning('未配置授权服务地址，仅清除了本地登录状态')
    window.location.href = '/login'
  }
}
</script>

<style scoped>
.portal {
  min-height: 100%;
  background: #f2f4f7;
}

.topbar {
  height: 60px;
  padding: 0 28px;
  background: #12263f;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.topbar-left {
  display: flex;
  align-items: center;
}

.logo {
  font-size: 18px;
  letter-spacing: 3px;
}

.divider {
  width: 1px;
  height: 18px;
  margin: 0 14px;
  background: rgba(255, 255, 255, 0.3);
}

.subtitle {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
}

.login-time {
  color: rgba(255, 255, 255, 0.55);
}

.content {
  padding: 24px 28px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-head h2 {
  margin: 0;
  font-size: 17px;
  color: #1f2d3d;
}

.section-desc {
  margin: 8px 0 0;
  font-size: 13px;
  color: #8a96a3;
}

.chain-alert {
  margin-bottom: 16px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.subsystem-card {
  border-radius: 10px;
}

.subsystem-card.is-offline {
  opacity: 0.6;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.card-desc {
  margin: 12px 0 16px;
  font-size: 12px;
  color: #8a96a3;
  min-height: 18px;
}

.card-actions {
  text-align: right;
}
</style>
