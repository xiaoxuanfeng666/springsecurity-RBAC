<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="本页只做查询：真实的积分变动不在本期范围内，表中的流水为演示数据。"
        style="margin-bottom: 12px"
      />

      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="用户名">
            <el-input
              v-model="query.username"
              placeholder="请输入用户名"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="query.changeType" placeholder="全部" clearable style="width: 150px">
              <el-option label="完成积分任务" value="TASK" />
              <el-option label="抽奖消耗" value="LOTTERY" />
              <el-option label="人工调整" value="ADMIN" />
            </el-select>
          </el-form-item>
          <el-form-item label="发生时间">
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 360px"
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
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column label="类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.changeType)">{{ typeText(row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="积分变动" width="110" align="center">
          <template #default="{ row }">
            <span :class="row.points >= 0 ? 'points-up' : 'points-down'">
              {{ row.points >= 0 ? '+' + row.points : row.points }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="变动后余额" width="110" align="center" />
        <el-table-column prop="source" label="来源" min-width="140" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
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
import { pageFlows } from '@/api/points'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const timeRange = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  changeType: null
})

function typeText(type) {
  return { TASK: '完成积分任务', LOTTERY: '抽奖消耗', ADMIN: '人工调整' }[type] || type
}

function typeTag(type) {
  return { TASK: 'success', LOTTERY: 'warning', ADMIN: 'info' }[type] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageFlows({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      changeType: query.changeType || undefined,
      beginTime: timeRange.value?.[0] || undefined,
      endTime: timeRange.value?.[1] || undefined
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
  query.changeType = null
  timeRange.value = []
  query.pageNum = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.points-up {
  color: #67c23a;
  font-weight: 600;
}

.points-down {
  color: #f56c6c;
  font-weight: 600;
}
</style>
