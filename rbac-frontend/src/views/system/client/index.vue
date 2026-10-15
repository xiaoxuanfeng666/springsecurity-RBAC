<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="客户端标识">
            <el-input
              v-model="query.appId"
              placeholder="请输入客户端标识"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-button v-permission="'system:client:add'" type="primary" :icon="Plus" @click="openCreate">
          新增客户端
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="appId" label="客户端标识" min-width="160" show-overflow-tooltip />
        <el-table-column prop="appSecret" label="客户端密钥" min-width="160" show-overflow-tooltip />
        <el-table-column label="授权模式" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="type in splitValues(row.grantTypes)" :key="type" size="small" class="tag-gap">
              {{ type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scope" label="授权范围" min-width="150" show-overflow-tooltip />
        <el-table-column prop="redirectUrl" label="回调地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="令牌有效期" min-width="170" align="center">
          <template #default="{ row }">
            <div>访问：{{ formatValidity(row.accessTokenValidity) }}</div>
            <div>刷新：{{ formatValidity(row.refreshTokenValidity) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:client:edit'" link type="warning" @click="openEdit(row)">
              修改
            </el-button>
            <el-button v-permission="'system:client:remove'" link type="danger" @click="handleDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改客户端' : '新增客户端'" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-form-item label="客户端标识" prop="appId">
          <el-input v-model="form.appId" :disabled="isEdit" placeholder="如 third-party-app，需唯一" />
        </el-form-item>
        <el-form-item label="客户端密钥" :prop="isEdit ? undefined : 'appSecret'">
          <el-input
            v-model="form.appSecret"
            type="password"
            show-password
            :placeholder="isEdit ? '留空表示不修改密钥' : '请输入客户端密钥'"
          />
        </el-form-item>
        <el-form-item label="授权模式" prop="grantTypes">
          <el-select v-model="form.grantTypes" multiple placeholder="请选择授权模式" style="width: 100%">
            <el-option
              v-for="type in GRANT_TYPES"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="授权范围">
          <el-input v-model="form.scope" placeholder="多个用英文逗号分隔，如 userinfo,openid" />
        </el-form-item>
        <el-form-item label="回调地址">
          <el-input
            v-model="form.redirectUrl"
            type="textarea"
            :rows="2"
            placeholder="多个用英文逗号分隔，需与第三方系统一致"
          />
        </el-form-item>
        <el-form-item label="资源ID">
          <el-input v-model="form.resourceIds" placeholder="多个用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="客户端权限">
          <el-input v-model="form.authorities" placeholder="多个用英文逗号分隔，如 ROLE_CLIENT" />
        </el-form-item>
        <el-form-item label="自动放行范围">
          <el-input v-model="form.autoApproveScopes" placeholder="多个用英文逗号分隔，true 表示全部自动放行" />
        </el-form-item>
        <el-form-item label="访问令牌有效期">
          <el-input-number v-model="form.accessTokenValidity" :min="1" :step="300" />
          <span class="unit-hint">秒</span>
        </el-form-item>
        <el-form-item label="刷新令牌有效期">
          <el-input-number v-model="form.refreshTokenValidity" :min="1" :step="3600" />
          <span class="unit-hint">秒</span>
        </el-form-item>
        <el-form-item label="扩展信息">
          <el-input
            v-model="form.additionalInformation"
            type="textarea"
            :rows="2"
            placeholder="JSON 格式，可留空"
          />
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
import { pageClients, createClient, updateClient, deleteClient } from '@/api/client'

/** 授权模式可选值，与后端 clientdetails.grantTypes 的多值约定一致 */
const GRANT_TYPES = [
  { value: 'authorization_code', label: '授权码模式 authorization_code' },
  { value: 'client_credentials', label: '客户端凭证 client_credentials' },
  { value: 'password', label: '密码模式 password' },
  { value: 'refresh_token', label: '刷新令牌 refresh_token' },
  { value: 'implicit', label: '简化模式 implicit' }
]

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  appId: ''
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  appId: '',
  appSecret: '',
  grantTypes: [],
  scope: '',
  redirectUrl: '',
  resourceIds: '',
  authorities: '',
  autoApproveScopes: '',
  accessTokenValidity: 900,
  refreshTokenValidity: 604800,
  additionalInformation: ''
})
const form = reactive(defaultForm())

const formRules = {
  appId: [{ required: true, message: '请输入客户端标识', trigger: 'blur' }],
  appSecret: [{ required: true, message: '请输入客户端密钥', trigger: 'blur' }],
  grantTypes: [{ required: true, type: 'array', message: '请至少选择一种授权模式', trigger: 'change' }]
}

function splitValues(value) {
  if (!value) {
    return []
  }
  return value.split(',').filter((item) => item)
}

function formatValidity(seconds) {
  return seconds ? `${seconds} 秒` : '默认'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageClients({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      appId: query.appId || undefined
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
  query.appId = ''
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
    appId: row.appId,
    // 回填明文密钥（后端解密后返回），便于核对与修改；
    // 清空后保存仍表示保持原密钥不变
    appSecret: row.appSecret || '',
    grantTypes: splitValues(row.grantTypes),
    scope: row.scope,
    redirectUrl: row.redirectUrl,
    resourceIds: row.resourceIds,
    authorities: row.authorities,
    autoApproveScopes: row.autoApproveScopes,
    accessTokenValidity: row.accessTokenValidity,
    refreshTokenValidity: row.refreshTokenValidity,
    additionalInformation: row.additionalInformation
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
    const payload = { ...form, grantTypes: form.grantTypes.join(',') }
    if (isEdit.value) {
      await updateClient(payload)
      ElMessage.success('修改成功')
    } else {
      await createClient(payload)
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
      `确定要删除客户端「${row.appId}」吗？删除后该第三方系统将无法再发起授权。`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  await deleteClient(row.appId)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.tag-gap {
  margin-right: 4px;
}

.unit-hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
