<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="来源系统">
            <el-select v-model="query.source" placeholder="全部" clearable style="width: 130px">
              <el-option v-for="s in SOURCES" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="模块">
            <el-input
              v-model="query.module"
              placeholder="如 用户管理"
              clearable
              style="width: 130px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="动作">
            <el-select v-model="query.action" placeholder="全部" clearable style="width: 120px">
              <el-option v-for="a in ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作人">
            <el-input
              v-model="query.operatorName"
              placeholder="用户名"
              clearable
              style="width: 120px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="query.success" placeholder="全部" clearable style="width: 100px">
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
        <el-table-column label="来源系统" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ sourceLabel(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="110">
          <template #default="{ row }">{{ row.module || '-' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="actionMeta(row.action).type" size="small">
              {{ actionMeta(row.action).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="请求" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.requestUri">{{ row.httpMethod }} {{ row.requestUri }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80" align="center">
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
        <el-table-column prop="ip" label="来源IP" width="125">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="发生时间" width="170" />
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="showDetail(row)">请求参数</el-button>
          </template>
        </el-table-column>
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

    <el-drawer v-model="drawerVisible" title="审计详情" size="46%">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="来源系统">{{ sourceLabel(detail.source) }}</el-descriptions-item>
          <el-descriptions-item label="模块">{{ detail.module || '-' }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ actionMeta(detail.action).label }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.operatorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求方法">{{ detail.httpMethod || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求路径">{{ detail.requestUri || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            {{ detail.success === 0 ? '失败' : '成功' }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ detail.durationMs == null ? '-' : detail.durationMs + ' ms' }}
          </el-descriptions-item>
          <el-descriptions-item label="来源IP">{{ detail.ip || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">
            {{ detail.description || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMsg" label="失败原因" :span="2">
            {{ detail.errorMsg }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="params-title">请求参数</div>
        <pre v-if="formattedParams" class="params-block">{{ formattedParams }}</pre>
        <el-empty v-else description="该操作没有记录请求参数" :image-size="60" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import { pageAudits, getAuditDetail } from '@/api/audit'

/** 来源系统：与各后端上报时的 source 保持一致 */
const SOURCES = [
  { value: 'rbac', label: 'RBAC 后台' },
  { value: 'marketing', label: '营销系统' },
  { value: 'order-center', label: '订单中心' }
]

/** 与各后端 AuditAction 保持一致；本系统只收增删改 */
const ACTIONS = [
  { value: 'CREATE', label: '新增', type: 'success' },
  { value: 'UPDATE', label: '修改', type: 'primary' },
  { value: 'DELETE', label: '删除', type: 'danger' },
  { value: 'GRANT', label: '授权', type: 'warning' }
]

const UNKNOWN_ACTION = { label: '-', type: 'info' }

function actionMeta(action) {
  return ACTIONS.find((a) => a.value === action) || UNKNOWN_ACTION
}

function sourceLabel(source) {
  const found = SOURCES.find((s) => s.value === source)
  return found ? found.label : source || '-'
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref(null)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  source: null,
  module: '',
  action: null,
  operatorName: '',
  success: null
})

const drawerVisible = ref(false)
const detail = ref(null)

const formattedParams = computed(() => {
  const raw = detail.value?.requestParams
  if (!raw) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch (e) {
    return raw
  }
})

async function loadData() {
  loading.value = true
  try {
    const [startTime, endTime] = dateRange.value || []
    const res = await pageAudits({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      source: query.source || undefined,
      module: query.module || undefined,
      action: query.action || undefined,
      operatorName: query.operatorName || undefined,
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

async function showDetail(row) {
  detail.value = row
  drawerVisible.value = true
  try {
    const res = await getAuditDetail(row.id)
    detail.value = res.data
  } catch (e) {
    // 详情接口失败时回退到列表行里的数据（列表同样带 requestParams）
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.source = null
  query.module = ''
  query.action = null
  query.operatorName = ''
  query.success = null
  dateRange.value = null
  query.pageNum = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.params-title {
  margin: 18px 0 8px;
  font-weight: 600;
}

.params-block {
  margin: 0;
  padding: 12px;
  max-height: 520px;
  overflow: auto;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
