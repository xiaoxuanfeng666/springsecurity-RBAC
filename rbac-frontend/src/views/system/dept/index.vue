<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="toolbar-title">组织架构（公司 / 分公司 / 部门）</span>
        <div>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          <el-button v-permission="'system:dept:add'" type="primary" :icon="Plus" @click="openCreate(null)">
            新增根节点
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="deptName" label="名称" min-width="220" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="leader" label="负责人" min-width="120" />
        <el-table-column prop="phone" label="联系电话" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:dept:add'" link type="primary" @click="openCreate(row)">
              新增下级
            </el-button>
            <el-button v-permission="'system:dept:edit'" link type="warning" @click="openEdit(row)">
              修改
            </el-button>
            <el-button v-permission="'system:dept:remove'" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改节点' : '新增节点'" width="540px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="上级节点">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'deptName', children: 'children', value: 'id' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :label="1">公司</el-radio>
            <el-radio :label="2">分公司</el-radio>
            <el-radio :label="3">部门</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import { getDeptTree, createDept, updateDept, deleteDept } from '@/api/dept'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
  parentId: 0,
  deptName: '',
  type: 3,
  leader: '',
  phone: '',
  email: '',
  sort: 0,
  status: 1
})
const form = reactive(defaultForm())

const formRules = {
  deptName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const parentOptions = computed(() => [
  { id: 0, deptName: '顶级节点', children: [] },
  ...tableData.value
])

function typeText(type) {
  return { 1: '公司', 2: '分公司', 3: '部门' }[type] || '部门'
}

function typeTag(type) {
  return { 1: 'danger', 2: 'warning', 3: 'primary' }[type] || 'primary'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getDeptTree()
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate(parent) {
  isEdit.value = false
  Object.assign(form, defaultForm())
  form.parentId = parent ? parent.id : 0
  // 在公司下新增默认建分公司，在分公司/部门下新增默认建部门
  form.type = parent ? (parent.type === 1 ? 2 : 3) : 1
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId,
    deptName: row.deptName,
    type: row.type,
    leader: row.leader,
    phone: row.phone,
    email: row.email,
    sort: row.sort,
    status: row.status
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
      await updateDept({ ...form })
      ElMessage.success('修改成功')
    } else {
      await createDept({ ...form })
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
    await ElMessageBox.confirm(`确定要删除「${row.deptName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await deleteDept(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.toolbar-title {
  font-size: 15px;
  font-weight: 500;
}
</style>
