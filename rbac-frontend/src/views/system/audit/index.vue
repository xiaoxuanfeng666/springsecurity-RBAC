<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="用户名">
            <el-input
              v-model="query.username"
              placeholder="目标用户名"
              clearable
              style="width: 150px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="动作">
            <el-select v-model="query.action" placeholder="全部" clearable style="width: 150px">
              <el-option v-for="a in ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="时间">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="username" label="目标用户" min-width="120">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="actionMeta(row.action).type" size="small">
              {{ actionMeta(row.action).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" min-width="120">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="说明" min-width="170" show-overflow-tooltip />
        <el-table-column prop="ip" label="来源IP" min-width="130">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="发生时间" min-width="170" />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { pageAudits } from '@/api/audit'

/** 与后端 AuditAction 保持一致 */
const ACTIONS = [
  { value: 'LOGIN', label: '登录', type: 'success' },
  { value: 'LOGOUT', label: '登出', type: 'info' },
  { value: 'KICK', label: '强制下线', type: 'danger' },
  { value: 'DISABLED', label: '停用下线', type: 'warning' },
  { value: 'RESIGNED', label: '离职下线', type: 'danger' },
  { value: 'RESET_PWD', label: '重置密码下线', type: 'warning' },
  { value: 'CHANGE_PWD', label: '自助改密', type: 'info' }
]

const UNKNOWN_ACTION = { label: '-', type: 'info' }

function actionMeta(action) {
  return ACTIONS.find((a) => a.value === action) || UNKNOWN_ACTION
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref(null)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  action: null
})

async function loadData() {
  loading.value = true
  try {
    const [startTime, endTime] = dateRange.value || []
    const res = await pageAudits({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      action: query.action || undefined,
      startTime: startTime || undefined,
      endTime: endTime || undefined
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
  query.action = null
  dateRange.value = null
  query.pageNum = 1
  loadData()
}

onMounted(loadData)
</script>
