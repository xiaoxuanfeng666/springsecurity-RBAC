<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="订单编号">
            <el-input
              v-model="query.orderNo"
              placeholder="请输入订单编号"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="客户名称">
            <el-input
              v-model="query.customerName"
              placeholder="请输入客户名称"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="订单状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
              <el-option
                v-for="item in STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-button v-permission="'order:order:add'" type="primary" :icon="Plus" @click="openCreate">
          新增订单
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="orderNo" label="订单编号" min-width="160" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column prop="orderAmount" label="订单金额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.orderAmount) }}</template>
        </el-table-column>
        <el-table-column label="订单状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" min-width="160" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'order:order:edit'" link type="warning" @click="openEdit(row)">
              修改
            </el-button>
            <el-button
              v-permission="'order:order:remove'"
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改订单' : '新增订单'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="订单编号" prop="orderNo">
          <el-input v-model="form.orderNo" placeholder="如 ORD20261016001，需唯一" />
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input v-model="form.customerName" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="订单金额" prop="orderAmount">
          <el-input-number
            v-model="form.orderAmount"
            :min="0"
            :precision="2"
            :step="10"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="订单状态">
          <el-select v-model="form.status" style="width: 200px">
            <el-option
              v-for="item in STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="下单时间">
          <el-date-picker
            v-model="form.orderTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择下单时间"
            style="width: 100%"
          />
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
import { pageOrders, createOrder, updateOrder, deleteOrder } from '@/api/order'

/** 状态字典与后端 OrderInfo 的常量一一对应 */
const STATUS_OPTIONS = [
  { value: 1, label: '待支付', tag: 'info' },
  { value: 2, label: '已支付', tag: 'primary' },
  { value: 3, label: '已发货', tag: 'warning' },
  { value: 4, label: '已完成', tag: 'success' },
  { value: 5, label: '已取消', tag: 'danger' }
]

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  orderNo: '',
  customerName: '',
  status: null
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
  orderNo: '',
  customerName: '',
  orderAmount: 0,
  status: 1,
  orderTime: '',
  remark: ''
})
const form = reactive(defaultForm())

const formRules = {
  orderNo: [{ required: true, message: '请输入订单编号', trigger: 'blur' }],
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  orderAmount: [{ required: true, message: '请输入订单金额', trigger: 'blur' }]
}

function statusText(status) {
  return STATUS_OPTIONS.find((s) => s.value === status)?.label || status
}

function statusTagType(status) {
  return STATUS_OPTIONS.find((s) => s.value === status)?.tag || 'info'
}

function formatAmount(amount) {
  const value = Number(amount)
  return Number.isNaN(value) ? '-' : value.toFixed(2)
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageOrders({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      orderNo: query.orderNo || undefined,
      customerName: query.customerName || undefined,
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
  query.orderNo = ''
  query.customerName = ''
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
    orderNo: row.orderNo,
    customerName: row.customerName,
    orderAmount: Number(row.orderAmount),
    status: row.status,
    orderTime: row.orderTime,
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
      await updateOrder({ ...form })
      ElMessage.success('修改成功')
    } else {
      await createOrder({ ...form })
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
    await ElMessageBox.confirm(`确定要删除订单「${row.orderNo}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await deleteOrder(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
