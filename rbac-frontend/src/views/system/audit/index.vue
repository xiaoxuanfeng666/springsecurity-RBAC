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
              style="width: 130px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="模块">
            <el-select v-model="query.module" placeholder="全部" clearable style="width: 130px">
              <el-option v-for="m in MODULES" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="动作">
            <el-select v-model="query.action" placeholder="全部" clearable style="width: 150px">
              <el-option v-for="a in ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="query.success" placeholder="全部" clearable style="width: 110px">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
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
              style="width: 250px"
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
        <el-table-column prop="module" label="模块" width="110">
          <template #default="{ row }">{{ row.module || '-' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="125" align="center">
          <template #default="{ row }">
            <el-tag :type="actionMeta(row.action).type" size="small">
              {{ actionMeta(row.action).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="目标用户" width="110">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="150" show-overflow-tooltip />
        <el-table-column label="请求" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.requestUri">{{ row.httpMethod }} {{ row.requestUri }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success === 0 ? 'danger' : 'success'" size="small">
              {{ row.success === 0 ? '失败' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="center">
          <template #default="{ row }">
            {{ row.durationMs == null ? '-' : row.durationMs + ' ms' }}
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="来源IP" width="130">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="发生时间" width="170" />
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
  { value: 'ENABLED', label: '启用', type: 'success' },
  { value: 'RESIGNED', label: '离职下线', type: 'danger' },
  { value: 'RESET_PWD', label: '重置密码下线', type: 'warning' },
  { value: 'CHANGE_PWD', label: '自助改密', type: 'info' },
  { value: 'CREATE', label: '新增', type: 'success' },
  { value: 'UPDATE', label: '修改', type: 'primary' },
  { value: 'DELETE', label: '删除', type: 'danger' },
  { value: 'GRANT', label: '授权', type: 'warning' }
]

const MODULES = ['认证', '用户管理', '角色管理', '菜单管理', '部门管理', '岗位管理']

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
  module: null,
  action: null,
  success: null
})

async function loadData() {
  loading.value = true
  try {
    const [startTime, endTime] = dateRange.value || []
    const res = await pageAudits({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      module: query.module || undefined,
      action: query.action || undefined,
      success: query.success === null ? undefined : query.success,
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
  query.module = null
  query.action = null
  query.success = null
  dateRange.value = null
  query.pageNum = 1
  loadData()
}

onMounted(loadData)
</script>
