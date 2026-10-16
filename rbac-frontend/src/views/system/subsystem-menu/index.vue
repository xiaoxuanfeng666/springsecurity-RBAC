<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="toolbar-title">子系统菜单</span>
          <el-select
            v-model="subsystemId"
            placeholder="请选择子系统"
            style="width: 220px"
            @change="loadData"
          >
            <el-option
              v-for="s in subsystems"
              :key="s.id"
              :label="s.subsystemName"
              :value="s.id"
            />
          </el-select>
        </div>
        <div>
          <el-button :icon="Refresh" :disabled="!subsystemId" @click="loadData">刷新</el-button>
          <el-button
            v-permission="'system:subsystem:menu:add'"
            type="primary"
            :icon="Plus"
            :disabled="!subsystemId"
            @click="openCreate(null)"
          >
            新增根菜单
          </el-button>
        </div>
      </div>

      <el-empty v-if="!subsystemId" description="请先选择子系统" />

      <el-table
        v-else
        v-loading="loading"
        :data="tableData"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="180" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="perms" label="权限标识" min-width="200" show-overflow-tooltip />
        <el-table-column prop="path" label="路由地址" min-width="140" />
        <el-table-column prop="component" label="组件路径" min-width="200" show-overflow-tooltip />
        <el-table-column prop="icon" label="图标" width="120" align="center" />
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.type !== 3"
              v-permission="'system:subsystem:menu:add'"
              link
              type="primary"
              @click="openCreate(row)"
            >
              新增子菜单
            </el-button>
            <el-button
              v-permission="'system:subsystem:menu:edit'"
              link
              type="warning"
              @click="openEdit(row)"
            >
              修改
            </el-button>
            <el-button
              v-permission="'system:subsystem:menu:remove'"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改子系统菜单' : '新增子系统菜单'" width="560px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这里的菜单与权限标识只用于下发给子系统，由子系统自己鉴权，不参与本系统的权限判定。"
        style="margin-bottom: 12px"
      />
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', children: 'children', value: 'id' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :label="1">目录</el-radio>
            <el-radio :label="2">菜单</el-radio>
            <el-radio :label="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item v-if="form.type === 3" label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="如 marketing:activity:add" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" label="路由地址">
          <el-input v-model="form.path" placeholder="如 /order 或 list" />
        </el-form-item>
        <el-form-item v-if="form.type === 2" label="组件路径">
          <el-input v-model="form.component" placeholder="子系统前端里的组件路径，如 order/list/index" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" label="图标">
          <el-input v-model="form.icon" placeholder="图标名，如 Document" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" label="是否显示">
          <el-radio-group v-model="form.visible">
            <el-radio :label="1">显示</el-radio>
            <el-radio :label="0">隐藏</el-radio>
          </el-radio-group>
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
import { listSubsystems } from '@/api/subsystem'
import {
  getSubsystemMenuTree,
  createSubsystemMenu,
  updateSubsystemMenu,
  deleteSubsystemMenu
} from '@/api/subsystemMenu'

const loading = ref(false)
const submitting = ref(false)
const subsystems = ref([])
const subsystemId = ref(null)
const tableData = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
  subsystemId: null,
  parentId: 0,
  menuName: '',
  type: 2,
  perms: '',
  path: '',
  component: '',
  icon: '',
  sort: 0,
  visible: 1,
  status: 1
})
const form = reactive(defaultForm())

const formRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  perms: [{ required: true, message: '请输入权限标识', trigger: 'blur' }]
}

// 上级菜单选项：顶级 + 当前子系统的目录/菜单。
// 按钮不能当父节点（按钮下再挂按钮没有意义），所以要从候选里剔掉。
function withoutButtons(nodes) {
  return nodes
    .filter((n) => n.type !== 3)
    .map((n) => ({ ...n, children: withoutButtons(n.children || []) }))
}

const parentOptions = computed(() => [
  { id: 0, menuName: '顶级菜单', children: [] },
  ...withoutButtons(tableData.value)
])

function typeText(type) {
  return { 1: '目录', 2: '菜单', 3: '按钮' }[type] || '-'
}

function typeTag(type) {
  return { 1: 'warning', 2: 'primary', 3: 'success' }[type] || 'info'
}

async function loadSubsystems() {
  const res = await listSubsystems()
  subsystems.value = res.data || []
  if (subsystems.value.length > 0) {
    subsystemId.value = subsystems.value[0].id
    await loadData()
  }
}

async function loadData() {
  if (!subsystemId.value) {
    tableData.value = []
    return
  }
  loading.value = true
  try {
    const res = await getSubsystemMenuTree(subsystemId.value)
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate(parent) {
  isEdit.value = false
  Object.assign(form, defaultForm())
  form.subsystemId = subsystemId.value
  form.parentId = parent ? parent.id : 0
  // 默认类型跟着父节点走：目录下建菜单、菜单下建按钮
  form.type = parent ? (parent.type === 1 ? 2 : 3) : 1
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    subsystemId: row.subsystemId,
    parentId: row.parentId,
    menuName: row.menuName,
    type: row.type,
    perms: row.perms,
    path: row.path,
    component: row.component,
    icon: row.icon,
    sort: row.sort,
    visible: row.visible,
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
      await updateSubsystemMenu({ ...form })
      ElMessage.success('修改成功')
    } else {
      await createSubsystemMenu({ ...form })
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
    await ElMessageBox.confirm(`确定要删除「${row.menuName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await deleteSubsystemMenu(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadSubsystems)
</script>

<style scoped>
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 500;
}
</style>
