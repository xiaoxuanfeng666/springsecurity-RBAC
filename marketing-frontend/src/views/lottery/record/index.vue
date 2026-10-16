<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="本页只做查询：真实的抽奖动作不在本期范围内，表中的记录为演示数据。"
        style="margin-bottom: 12px"
      />

      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="活动">
            <el-select v-model="query.activityId" placeholder="全部活动" clearable style="width: 180px">
              <el-option v-for="a in activities" :key="a.id" :label="a.activityName" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="用户名">
            <el-input
              v-model="query.username"
              placeholder="请输入用户名"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="query.win" placeholder="全部" clearable style="width: 120px">
              <el-option label="中奖" :value="1" />
              <el-option label="未中奖" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="抽奖时间">
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
        <el-table-column prop="activityName" label="活动名称" min-width="150" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column label="抽奖结果" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.win === 1 ? 'success' : 'info'" size="small">
              {{ row.win === 1 ? '中奖' : '未中奖' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="prizeLevel" label="奖品等级" width="110" align="center" />
        <el-table-column prop="prizeName" label="奖品名称" min-width="140" />
        <el-table-column prop="drawTime" label="抽奖时间" min-width="170" />
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
import { pageRecords, listActivities } from '@/api/lottery'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const activities = ref([])
const timeRange = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  activityId: null,
  username: '',
  win: null
})

async function loadActivities() {
  const res = await listActivities()
  activities.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageRecords({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      activityId: query.activityId ?? undefined,
      username: query.username || undefined,
      win: query.win ?? undefined,
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
  query.activityId = null
  query.username = ''
  query.win = null
  timeRange.value = []
  query.pageNum = 1
  loadData()
}

onMounted(() => {
  loadActivities()
  loadData()
})
</script>
