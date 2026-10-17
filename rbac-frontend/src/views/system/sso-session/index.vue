<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-form :inline="true" :model="query" @submit.prevent>
          <el-form-item label="用户名">
            <el-input
              v-model="query.username"
              placeholder="请输入用户名"
              clearable
              style="width: 160px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
              <el-option label="在线" :value="1" />
              <el-option label="已登出" :value="0" />
              <el-option label="被强制下线" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="username" label="用户名" min-width="110" />
        <el-table-column prop="nickname" label="昵称" min-width="110">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="device" label="设备" min-width="140">
          <template #default="{ row }">{{ row.device || '-' }}</template>
        </el-table-column>
        <el-table-column prop="loginIp" label="登录IP" min-width="130">
          <template #default="{ row }">{{ row.loginIp || '-' }}</template>
        </el-table-column>
        <el-table-column prop="loginTime" label="登录时间" min-width="170" />
        <el-table-column prop="logoutTime" label="登出时间" min-width="170">
          <template #default="{ row }">{{ row.logoutTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">
              {{ statusMeta(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="logoutReason" label="登出原因" min-width="130">
          <template #default="{ row }">{{ row.logoutReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              v-permission="'system:sso:kick'"
              link
              type="danger"
              @click="handleKick(row)"
            >
              强制下线
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { pageSsoSessions, kickSsoSession } from '@/api/ssoSession'

/** 与后端 SsoSessionRecord 的状态常量一致：1 在线 / 0 已登出 / 2 被强制下线 */
const STATUS_META = {
  1: { text: '在线', type: 'success' },
  0: { text: '已登出', type: 'info' },
  2: { text: '被强制下线', type: 'danger' }
}

function statusMeta(status) {
  return STATUS_META[status] || { text: '-', type: 'info' }
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  status: null
})

async function loadData() {
  loading.value = true
  try {
    const res = await pageSsoSessions({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      status: query.status === null ? undefined : query.status
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.username = ''
  query.status = null
  query.pageNum = 1
  loadData()
}

async function handleKick(row) {
  try {
    await ElMessageBox.confirm(
      `确定要强制下线用户「${row.username}」吗？该用户当前的登录状态会立即失效。`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (e) {
    return
  }
  await kickSsoSession(row.id)
  ElMessage.success('已强制下线')
  loadData()
}

onMounted(loadData)
</script>
