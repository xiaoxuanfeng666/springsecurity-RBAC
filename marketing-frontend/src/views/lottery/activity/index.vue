<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="活动名称">
            <el-input
              v-model="query.activityName"
              placeholder="请输入活动名称"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-button v-permission="'marketing:activity:add'" type="primary" :icon="Plus" @click="openCreate">
          新增活动
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="activityName" label="活动名称" min-width="150" />
        <el-table-column prop="activityCode" label="活动编码" min-width="150" />
        <el-table-column prop="startTime" label="开始时间" min-width="160" />
        <el-table-column prop="endTime" label="结束时间" min-width="160" />
        <el-table-column prop="dailyLimit" label="每日上限" width="90" align="center" />
        <el-table-column prop="prizeDesc" label="奖品说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-permission="['marketing:activity:query', 'marketing:activity:edit']"
              link
              type="primary"
              @click="openPrizes(row)"
            >
              配置奖品
            </el-button>
            <el-button v-permission="'marketing:activity:edit'" link type="warning" @click="openEdit(row)">
              修改
            </el-button>
            <el-button
              v-permission="'marketing:activity:remove'"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
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

    <!-- 活动表单 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改抽奖活动' : '新增抽奖活动'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="活动名称" prop="activityName">
          <el-input v-model="form.activityName" placeholder="请输入活动名称" />
        </el-form-item>
        <el-form-item label="活动编码" prop="activityCode">
          <el-input v-model="form.activityCode" placeholder="如 DOUBLE11_WHEEL，需唯一" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="每日抽奖上限">
          <el-input-number v-model="form.dailyLimit" :min="1" />
        </el-form-item>
        <el-form-item label="奖品说明">
          <el-input v-model="form.prizeDesc" placeholder="给用户看的奖品说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 活动奖品配置 -->
    <el-dialog v-model="prizeVisible" :title="`配置奖品 - ${prizeActivityName}`" width="820px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="勾选表示该活动投放此奖品。概率之和可以小于 100%，差额即为不中奖的概率；合计不能超过 100%。"
        style="margin-bottom: 12px"
      />
      <el-table :data="prizeRows" border max-height="420">
        <el-table-column label="投放" width="70" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.associated" />
          </template>
        </el-table-column>
        <el-table-column prop="prizeName" label="奖品名称" min-width="140" />
        <el-table-column prop="prizeLevel" label="等级" width="100" align="center" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">{{ prizeTypeText(row.prizeType) }}</template>
        </el-table-column>
        <el-table-column prop="totalStock" label="总库存" width="90" align="center" />
        <el-table-column label="中奖概率(%)" width="150" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.probability"
              :min="0"
              :max="100"
              :precision="2"
              :step="0.5"
              :disabled="!row.associated"
              controls-position="right"
              style="width: 120px"
            />
          </template>
        </el-table-column>
        <el-table-column label="投放数量" width="150" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.stock"
              :min="0"
              :disabled="!row.associated"
              controls-position="right"
              style="width: 120px"
            />
          </template>
        </el-table-column>
      </el-table>
      <div class="prize-summary">
        已勾选 {{ checkedCount }} 个奖品，概率合计
        <b :class="{ 'over-limit': probabilityTotal > 100 }">{{ probabilityTotal.toFixed(2) }}%</b>
      </div>
      <template #footer>
        <el-button @click="prizeVisible = false">取消</el-button>
        <el-button
          v-permission="'marketing:activity:edit'"
          type="primary"
          :loading="prizeSubmitting"
          :disabled="probabilityTotal > 100"
          @click="handleSavePrizes"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  pageActivities,
  createActivity,
  updateActivity,
  deleteActivity,
  getActivityPrizes,
  updateActivityPrizes
} from '@/api/lottery'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  activityName: '',
  status: null
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
  activityName: '',
  activityCode: '',
  startTime: '',
  endTime: '',
  dailyLimit: 1,
  prizeDesc: '',
  status: 1,
  remark: ''
})
const form = reactive(defaultForm())

const formRules = {
  activityName: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  activityCode: [{ required: true, message: '请输入活动编码', trigger: 'blur' }]
}

const prizeVisible = ref(false)
const prizeSubmitting = ref(false)
const prizeActivityId = ref(null)
const prizeActivityName = ref('')
const prizeRows = ref([])

const checkedCount = computed(() => prizeRows.value.filter((r) => r.associated).length)
const probabilityTotal = computed(() =>
  prizeRows.value
    .filter((r) => r.associated)
    .reduce((sum, r) => sum + (Number(r.probability) || 0), 0)
)

function prizeTypeText(type) {
  return { GOODS: '实物', COUPON: '优惠券', POINTS: '积分', NONE: '不中奖' }[type] || type
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageActivities({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      activityName: query.activityName || undefined,
      status: query.status ?? undefined
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
  query.activityName = ''
  query.status = null
  query.pageNum = 1
  loadData()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    activityName: row.activityName,
    activityCode: row.activityCode,
    startTime: row.startTime,
    endTime: row.endTime,
    dailyLimit: row.dailyLimit,
    prizeDesc: row.prizeDesc,
    status: row.status,
    remark: row.remark
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateActivity({ ...form })
      ElMessage.success('修改成功')
    } else {
      await createActivity({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除活动「${row.activityName}」吗？该活动已配置的奖品会一并删除。`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  await deleteActivity(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function openPrizes(row) {
  const res = await getActivityPrizes(row.id)
  prizeActivityId.value = row.id
  prizeActivityName.value = row.activityName
  prizeRows.value = (res.data || []).map((item) => ({
    prizeId: item.prizeId,
    prizeName: item.prizeName,
    prizeLevel: item.prizeLevel,
    prizeType: item.prizeType,
    totalStock: item.totalStock,
    associated: !!item.associated,
    probability: item.probability === null || item.probability === undefined ? 0 : Number(item.probability),
    stock: item.stock === null || item.stock === undefined ? 0 : item.stock
  }))
  prizeVisible.value = true
}

async function handleSavePrizes() {
  const items = prizeRows.value
    .filter((r) => r.associated)
    .map((r, index) => ({
      prizeId: r.prizeId,
      probability: r.probability || 0,
      stock: r.stock || 0,
      sort: index + 1
    }))
  prizeSubmitting.value = true
  try {
    await updateActivityPrizes(prizeActivityId.value, items)
    ElMessage.success('奖品配置已保存')
    prizeVisible.value = false
  } finally {
    prizeSubmitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.prize-summary {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}

.over-limit {
  color: #f56c6c;
}
</style>
