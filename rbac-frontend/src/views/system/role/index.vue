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
        <el-table-column label="操作" width="360" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:role:edit'" link type="primary" @click="openEdit(row)">
              修改
            </el-button>
            <el-tooltip
              :disabled="!isSuperAdmin(row)"
              content="超级管理员隐式拥有全部权限，无需分配"
              placement="top"
            >
              <span>
                <el-button
                  v-permission="'system:role:menu'"
                  link
                  type="warning"
                  :disabled="isSuperAdmin(row)"
                  @click="openAssign(row)"
                >
                  分配权限
                </el-button>
              </span>
            </el-tooltip>
            <el-tooltip
              :disabled="!isSuperAdmin(row)"
              content="超级管理员隐式拥有全部权限，无需分配"
              placement="top"
            >
              <span>
                <el-button
                  v-permission="'system:role:menu'"
                  link
                  type="warning"
                  :disabled="isSuperAdmin(row)"
                  @click="openAssignSubsystem(row)"
                >
                  分配子系统菜单
                </el-button>
              </span>
            </el-tooltip>
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

    <!-- 分配子系统菜单 -->
    <el-dialog
      v-model="subAssignVisible"
      :title="`分配子系统菜单 - ${assignRole.roleName}`"
      width="520px"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这里配的是下发给子系统的导航菜单，不影响本系统的权限判定；按子系统分别保存，互不影响。"
        style="margin-bottom: 12px"
      />
      <el-select
        v-model="subAssignSubsystemId"
        placeholder="请选择子系统"
        style="width: 100%; margin-bottom: 12px"
        @change="loadSubsystemAssign"
      >
        <el-option
          v-for="s in subsystemOptions"
          :key="s.id"
          :label="s.subsystemName"
          :value="s.id"
        />
      </el-select>
      <el-empty v-if="!subAssignSubsystemId" description="请先选择子系统" :image-size="60" />
      <div v-else class="tree-wrapper">
        <el-tree
          ref="subTreeRef"
          :key="subAssignSubsystemId"
          :data="subMenuTree"
          :props="treeProps"
          node-key="id"
          show-checkbox
          default-expand-all
          :check-strictly="false"
        />
      </div>
      <template #footer>
        <el-button @click="subAssignVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!subAssignSubsystemId"
          @click="handleAssignSubsystem"
        >
          保存
        </el-button>
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
  updateRoleMenus,
  getRoleSubsystemMenuIds,
  updateRoleSubsystemMenus
} from '@/api/role'
import { getMenuTree } from '@/api/menu'
import { listSubsystems } from '@/api/subsystem'
import { getSubsystemMenuTree } from '@/api/subsystemMenu'

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

const subAssignVisible = ref(false)
const subAssignSubsystemId = ref(null)
const subsystemOptions = ref([])
const subMenuTree = ref([])
const subTreeRef = ref()

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

/** 超级管理员的权限由后端隐式授予（不落库），前端不允许再分配 */
function isSuperAdmin(row) {
  return row.roleKey === 'admin'
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

/** 打开「分配子系统菜单」弹窗 */
async function openAssignSubsystem(row) {
  assignRole.value = row
  if (subsystemOptions.value.length === 0) {
    const res = await listSubsystems()
    subsystemOptions.value = res.data || []
  }
  subAssignSubsystemId.value = subsystemOptions.value.length > 0 ? subsystemOptions.value[0].id : null
  subAssignVisible.value = true
  await loadSubsystemAssign()
}

/** 拉取所选子系统的菜单树 + 该角色在此子系统的勾选，用于回显 */
async function loadSubsystemAssign() {
  if (!subAssignSubsystemId.value) {
    subMenuTree.value = []
    return
  }
  const res = await getSubsystemMenuTree(subAssignSubsystemId.value)
  subMenuTree.value = res.data
  const owned = await getRoleSubsystemMenuIds(assignRole.value.id, subAssignSubsystemId.value)
  const ownedIds = owned.data || []
  const leafIds = collectLeafIds(subMenuTree.value)
  // 树带 :key，切换子系统会重建；等它渲染完再回显
  await nextTick()
  subTreeRef.value?.setCheckedKeys(ownedIds.filter((id) => leafIds.has(id)))
}

async function handleAssignSubsystem() {
  submitting.value = true
  try {
    const menuIds = [
      ...subTreeRef.value.getCheckedKeys(),
      ...subTreeRef.value.getHalfCheckedKeys()
    ]
    await updateRoleSubsystemMenus(assignRole.value.id, {
      subsystemId: subAssignSubsystemId.value,
      menuIds
    })
    ElMessage.success('子系统菜单已更新')
    subAssignVisible.value = false
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
