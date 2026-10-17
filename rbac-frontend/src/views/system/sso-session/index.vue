<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这里是经过统一门户/子系统登录的在线用户"
        description="「强制下线」会删除该用户的 SSO 会话并销毁其浏览器会话，他在所有子系统里的令牌下一个请求即失效。本系统后台自己的登录态（用户管理里的「强制下线」）与这里是两回事。"
        style="margin-bottom: 12px"
      />

      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="用户名">
            <el-input
              v-model="keyword"
              placeholder="用户名 / 昵称"
              clearable
              style="width: 170px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" :data="filteredData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column label="昵称" width="150">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column label="已登录的客户端" min-width="260">
          <template #default="{ row }">
            <template v-if="row.clients && row.clients.length">
              <el-tag v-for="c in row.clients" :key="c" size="small" style="margin-right: 6px">
                {{ c }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="loginTime" label="登录时间" width="180">
          <template #default="{ row }">{{ row.loginTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:sso:kick'"
              link
              type="danger"
              :disabled="row.username === currentUsername"
              @click="handleKick(row)"
            >
              强制下线
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listSsoSessions, kickSsoSession } from '@/api/ssoSession'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const loading = ref(false)
const tableData = ref([])
const keyword = ref('')

const currentUsername = computed(() => userStore.userInfo?.username || '')

/**
 * 客户端的过滤放在前端做：这张表的数据源是 Redis 里的会话（服务端一次性全量返回），
 * 本来就不大，再为它加一个分页/筛选接口不划算。
 */
const filteredData = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) {
    return tableData.value
  }
  return tableData.value.filter(
    (row) => (row.username || '').includes(kw) || (row.nickname || '').includes(kw)
  )
})

async function loadData() {
  loading.value = true
  try {
    const res = await listSsoSessions()
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // 过滤是响应式的，这里只是让「查询」按钮有反馈
  loadData()
}

async function handleKick(row) {
  try {
    await ElMessageBox.confirm(
      `确定让「${row.nickname || row.username}」下线吗？他在所有子系统里的登录会立即失效。`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  await kickSsoSession(row.userId)
  ElMessage.success('已强制下线')
  loadData()
}

onMounted(loadData)
</script>
