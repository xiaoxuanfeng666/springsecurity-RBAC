<template>
  <div class="page-container">
    <el-row :gutter="16" class="stat-row">
      <el-col :span="12">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">订单总数</div>
          <div class="stat-value">{{ stat.totalCount ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">订单金额合计</div>
          <div class="stat-value">{{ formatAmount(stat.totalAmount) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>按状态分布</span>
          <el-button :icon="Refresh" link @click="loadData">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="statusRows" border stripe>
        <el-table-column label="订单状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="row.tag" size="small">{{ row.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="订单数" width="120" align="right" />
        <el-table-column label="金额合计" width="160" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="占比" min-width="220">
          <template #default="{ row }">
            <el-progress :percentage="row.percentage" :stroke-width="14" />
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && statusRows.length === 0" description="暂无订单数据" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getOrderStat } from '@/api/order'

/** 与订单列表页、后端 OrderInfo 的状态字典保持一致 */
const STATUS_OPTIONS = [
  { value: 1, label: '待支付', tag: 'info' },
  { value: 2, label: '已支付', tag: 'primary' },
  { value: 3, label: '已发货', tag: 'warning' },
  { value: 4, label: '已完成', tag: 'success' },
  { value: 5, label: '已取消', tag: 'danger' }
]

const loading = ref(false)
const stat = ref({ totalCount: null, totalAmount: null, statusCounts: [] })

/** 后端只返回有数据的状态，这里补上标签与占比，空状态仍然显示 0 */
const statusRows = computed(() => {
  const total = Number(stat.value.totalCount) || 0
  const counts = new Map((stat.value.statusCounts || []).map((r) => [r.status, r]))

  return STATUS_OPTIONS.map((option) => {
    const row = counts.get(option.value)
    const count = row ? Number(row.count) || 0 : 0
    return {
      status: option.value,
      label: option.label,
      tag: option.tag,
      count,
      amount: row ? row.amount : 0,
      percentage: total === 0 ? 0 : Math.round((count / total) * 10000) / 100
    }
  })
})

function formatAmount(amount) {
  if (amount === null || amount === undefined) {
    return '-'
  }
  const value = Number(amount)
  return Number.isNaN(value) ? '-' : value.toFixed(2)
}

async function loadData() {
  loading.value = true
  try {
    const res = await getOrderStat()
    stat.value = res.data || { totalCount: 0, totalAmount: 0, statusCounts: [] }
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
