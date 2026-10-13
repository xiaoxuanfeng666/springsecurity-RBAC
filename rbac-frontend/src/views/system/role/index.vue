<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="角色名称">
            <el-input v-model="query.roleName" placeholder="请输入角色名称" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-button v-permission="'system:role:add'" type="primary" :icon="Plus" @click="openCreate">
          新增角色
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleKey" label="角色标识" min-width="140" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:role:edit'" link type="primary" @click="openEdit(row)">
              修改
            </el-button>
            <el-button v-permission="'system:role:menu'" link type="warning" @click="openAssign(row)">
              分配权限
            </el-button>
            <el-button v-permission="'system:role:remove'" link type="danger" @click="handleDelete(row)">
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

    <!-- 新增/编辑角色 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改角色' : '新增角色'" width="500px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="如 common，用于后端鉴权" />
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
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="assignVisible" :title="`分配权限 - ${assignRole.roleName}`" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="保存后立即对该角色的用户生效，用户无需重新登录。"
        style="margin-bottom: 12px"
      />
      <div class="tree-wrapper">
        <el-tree
          ref="treeRef"
          :data="menuTree"
          :props="treeProps"
          node-key="id"
          show-checkbox
          default-expand-all
          :check-strictly="false"
        >
          <template #default="{ data }">
            <span>
              {{ data.menuName }}
              <el-tag v-if="data.type === 3" size="small" type="info" style="margin-left: 6px">按钮</el-tag>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  pageRoles,
  createRole,
  updateRole,
  deleteRole,
  getRoleMenuIds,
  updateRoleMenus
} from '@/api/role'
import { getMenuTree } from '@/api/menu'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  roleName: ''
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
  roleName: '',
  roleKey: '',
  sort: 0,
  status: 1,
  remark: ''
})
const form = reactive(defaultForm())

const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }]
}

const assignVisible = ref(false)
const assignRole = ref({})
const menuTree = ref([])
const treeRef = ref()
const treeProps = { label: 'menuName', children: 'children' }

async function loadData() {
  loading.value = true
  try {
    const res = await pageRoles({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      roleName: query.roleName || undefined
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
  query.roleName = ''
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
    roleName: row.roleName,
    roleKey: row.roleKey,
    sort: row.sort,
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
      await updateRole({ ...form })
      ElMessage.success('修改成功')
    } else {
      await createRole({ ...form })
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
    await ElMessageBox.confirm(`确定要删除角色「${row.roleName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  loadData()
}

/** 收集所有叶子节点 ID，用于授权回显时避免父节点级联全选 */
function collectLeafIds(nodes, set = new Set()) {
  nodes.forEach((n) => {
    if (n.children && n.children.length > 0) {
      collectLeafIds(n.children, set)
    } else {
      set.add(n.id)
    }
  })
  return set
}

async function openAssign(row) {
  assignRole.value = row
  if (menuTree.value.length === 0) {
    const res = await getMenuTree()
    menuTree.value = res.data
  }
  assignVisible.value = true
  const res = await getRoleMenuIds(row.id)
  const ownedIds = res.data || []
  const leafIds = collectLeafIds(menuTree.value)
  // 只设置叶子节点，父节点由 el-tree 自动推导勾选/半选状态
  const checkedLeafIds = ownedIds.filter((id) => leafIds.has(id))
  await nextTick()
  treeRef.value.setCheckedKeys(checkedLeafIds)
}

async function handleAssign() {
  submitting.value = true
  try {
    // 保存叶子选中 + 半选父节点，保证菜单层级完整
    const menuIds = [
      ...treeRef.value.getCheckedKeys(),
      ...treeRef.value.getHalfCheckedKeys()
    ]
    await updateRoleMenus(assignRole.value.id, menuIds)
    ElMessage.success('权限已更新，立即对该角色用户生效')
    assignVisible.value = false
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.tree-wrapper {
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
}
</style>
