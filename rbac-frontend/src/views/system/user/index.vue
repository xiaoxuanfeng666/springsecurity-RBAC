<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 搜索区 -->
      <div class="table-toolbar">
        <el-form :inline="true" :model="query" @submit.prevent>
          <el-form-item label="用户名">
            <el-input v-model="query.username" placeholder="请输入用户名" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-button v-permission="'system:user:add'" type="primary" :icon="Plus" @click="openCreate">
          新增用户
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:user:edit'" link type="primary" @click="openEdit(row)">
              修改
            </el-button>
            <el-button
              v-permission="'system:user:edit'"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button v-permission="'system:user:resetPwd'" link type="info" @click="openResetPwd(row)">
              重置密码
            </el-button>
            <el-button v-permission="'system:user:remove'" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改用户' : '新增用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空表示不修改密码' : '请输入密码'"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
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

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="pwdDialogVisible" title="重置密码" width="420px">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">重置后该用户的登录凭证将立即失效，需要重新登录。</div>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  pageUsers,
  createUser,
  updateUser,
  deleteUser,
  updateUserStatus,
  getUserRoles,
  resetUserPassword
} from '@/api/user'
import { listAllRoles } from '@/api/role'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const roleOptions = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  status: null
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  status: 1,
  remark: '',
  roleIds: []
})
const form = reactive(defaultForm())

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!isEdit.value && !value) {
          callback(new Error('请输入密码'))
        } else if (value && value.length < 6) {
          callback(new Error('密码长度不能少于 6 位'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const pwdDialogVisible = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({ id: null, password: '' })
const pwdRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageUsers({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      status: query.status === null ? undefined : query.status
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  const res = await listAllRoles()
  roleOptions.value = res.data
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.username = ''
  query.status = null
  query.pageNum = 1
  loadData()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

async function openEdit(row) {
  isEdit.value = true
  Object.assign(form, defaultForm())
  Object.assign(form, {
    id: row.id,
    username: row.username,
    nickname: row.nickname,
    email: row.email,
    phone: row.phone,
    status: row.status,
    remark: row.remark,
    password: ''
  })
  const res = await getUserRoles(row.id)
  form.roleIds = res.data || []
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
      await updateUser({ ...form })
      ElMessage.success('修改成功')
    } else {
      await createUser({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const willDisable = row.status === 1
  const tip = willDisable
    ? '禁用后该用户会立即被强制下线，确定继续吗？'
    : '确定要启用该用户吗？'
  try {
    await ElMessageBox.confirm(tip, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await updateUserStatus(row.id, willDisable ? 0 : 1)
  ElMessage.success(willDisable ? '已禁用，该用户登录状态已失效' : '已启用')
  loadData()
}

function openResetPwd(row) {
  pwdForm.id = row.id
  pwdForm.password = ''
  pwdDialogVisible.value = true
}

async function handleResetPwd() {
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await resetUserPassword(pwdForm.id, pwdForm.password)
    ElMessage.success('密码已重置')
    pwdDialogVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${row.username}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
  loadRoles()
})
</script>

<style scoped>
.dialog-tip {
  margin-left: 90px;
  font-size: 12px;
  color: #e6a23c;
}
</style>
