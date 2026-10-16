<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="奖品名称">
            <el-input
              v-model="query.prizeName"
              placeholder="请输入奖品名称"
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

        <el-button v-permission="'marketing:prize:add'" type="primary" :icon="Plus" @click="openCreate">
          新增奖品
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="prizeName" label="奖品名称" min-width="150" />
        <el-table-column prop="prizeLevel" label="奖品等级" width="110" align="center" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.prizeType)">{{ typeText(row.prizeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="prizeValue" label="面值" width="90" align="center" />
        <el-table-column prop="totalStock" label="总库存" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'marketing:prize:edit'" link type="warning" @click="openEdit(row)">
              修改
            </el-button>
            <el-button v-permission="'marketing:prize:remove'" link type="danger" @click="handleDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改奖品' : '新增奖品'" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="奖品名称" prop="prizeName">
          <el-input v-model="form.prizeName" placeholder="如 100元优惠券" />
        </el-form-item>
        <el-form-item label="奖品等级" prop="prizeLevel">
          <el-input v-model="form.prizeLevel" placeholder="如 一等奖 / 谢谢参与" />
        </el-form-item>
        <el-form-item label="奖品类型" prop="prizeType">
          <el-radio-group v-model="form.prizeType">
            <el-radio label="GOODS">实物</el-radio>
            <el-radio label="COUPON">优惠券</el-radio>
            <el-radio label="POINTS">积分</el-radio>
            <el-radio label="NONE">不中奖</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="面值">
          <el-input-number v-model="form.prizeValue" :min="0" />
          <span class="form-tip">优惠券金额或积分值，实物可留空</span>
        </el-form-item>
        <el-form-item label="总库存">
          <el-input-number v-model="form.totalStock" :min="0" />
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { pagePrizes, createPrize, updatePrize, deletePrize } from '@/api/lottery'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  prizeName: '',
  status: null
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
  prizeName: '',
  prizeLevel: '',
  prizeType: 'COUPON',
  prizeValue: null,
  totalStock: 0,
  status: 1,
  remark: ''
})
const form = reactive(defaultForm())

const formRules = {
  prizeName: [{ required: true, message: '请输入奖品名称', trigger: 'blur' }],
  prizeLevel: [{ required: true, message: '请输入奖品等级', trigger: 'blur' }],
  prizeType: [{ required: true, message: '请选择奖品类型', trigger: 'change' }]
}

function typeText(type) {
  return { GOODS: '实物', COUPON: '优惠券', POINTS: '积分', NONE: '不中奖' }[type] || type
}

function typeTag(type) {
  return { GOODS: 'warning', COUPON: 'primary', POINTS: 'success', NONE: 'info' }[type] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pagePrizes({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      prizeName: query.prizeName || undefined,
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
  query.prizeName = ''
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
    prizeName: row.prizeName,
    prizeLevel: row.prizeLevel,
    prizeType: row.prizeType,
    prizeValue: row.prizeValue,
    totalStock: row.totalStock,
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
      await updatePrize({ ...form })
      ElMessage.success('修改成功')
    } else {
      await createPrize({ ...form })
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
      `确定要删除奖品「${row.prizeName}」吗？已被活动使用时无法删除。`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  await deletePrize(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
