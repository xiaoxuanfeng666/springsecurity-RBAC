<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="toolbar-title">菜单与权限</span>
        <div class="toolbar-actions">
          <el-button
            v-permission="'system:subsystem:add'"
            :icon="FolderAdd"
            @click="openCreateSubsystem"
          >
            新增子系统
          </el-button>
          <el-select
            v-model="scope"
            style="width: 200px; margin: 0 10px"
            @change="handleScopeChange"
          >
            <el-option label="RBAC 本系统" value="rbac" />
            <el-option
              v-for="s in visibleSubsystems"
              :key="s.id"
              :label="`${s.subsystemName}（${s.subsystemCode}）`"
              :value="String(s.id)"
            />
          </el-select>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          <el-button v-permission="'system:menu:add'" type="primary" :icon="Plus" @click="openCreate(null)">
            新增根菜单
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="isSubsystemScope"
        type="info"
        :closable="false"
        show-icon
        :title="`当前维护「${currentScopeName}」的菜单与按钮。保存后经 MQ 只下发给该子系统（唯一编码：${currentSubsystemCode}）。`"
        style="margin-bottom: 12px"
      />

      <el-table
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
        <el-table-column prop="path" label="路由地址" min-width="130" />
        <el-table-column prop="component" label="组件路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="180" />
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:menu:add'" link type="primary" @click="openCreate(row)">
              新增子菜单
            </el-button>
            <el-button v-permission="'system:menu:edit'" link type="warning" @click="openEdit(row)">
              修改
            </el-button>
            <el-button v-permission="'system:menu:remove'" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改菜单' : '新增菜单'" width="560px">
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
        <el-form-item v-if="form.type !== 3" label="路由地址">
          <el-input v-model="form.path" placeholder="如 /system 或 user" />
        </el-form-item>
        <el-form-item v-if="form.type === 2" label="组件路径">
          <el-input v-model="form.component" placeholder="如 system/user/index（相对 src/views）" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.perms" placeholder="如 system:user:list" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 User" />
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

    <!-- 新增子系统：名称 + 唯一编码 -->
    <el-dialog v-model="subsystemDialogVisible" title="新增子系统" width="480px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="编码是子系统的唯一标识，同时用作 MQ tag，只能包含字母、数字、下划线和短横线。"
        style="margin-bottom: 12px"
      />
      <el-form ref="subsystemFormRef" :model="subsystemForm" :rules="subsystemRules" label-width="100px">
        <el-form-item label="子系统名称" prop="subsystemName">
          <el-input v-model="subsystemForm.subsystemName" placeholder="如 营销系统" />
        </el-form-item>
        <el-form-item label="子系统编码" prop="subsystemCode">
          <el-input v-model="subsystemForm.subsystemCode" placeholder="如 marketing，创建后不可轻易变更" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="subsystemForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="subsystemForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="subsystemForm.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subsystemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="subsystemSubmitting" @click="handleCreateSubsystem">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, FolderAdd } from '@element-plus/icons-vue'
import { getMenuTree, createMenu, updateMenu, deleteMenu } from '@/api/menu'
import {
  getSubsystemMenuTree,
  createSubsystemMenu,
  updateSubsystemMenu,
  deleteSubsystemMenu
} from '@/api/subsystemMenu'
import { listSubsystems, createSubsystem } from '@/api/subsystem'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])

/** rbac = 维护本系统菜单；其他值 = 子系统 ID */
const scope = ref('rbac')
const subsystems = ref([])

const isSubsystemScope = computed(() => scope.value !== 'rbac')
const currentSubsystem = computed(
  () => subsystems.value.find((s) => String(s.id) === String(scope.value)) || null
)
const currentScopeName = computed(() =>
  currentSubsystem.value ? currentSubsystem.value.subsystemName : 'RBAC 本系统'
)
const currentSubsystemCode = computed(() =>
  currentSubsystem.value ? currentSubsystem.value.subsystemCode : '-'
)
/** 只有拥有子系统菜单管理权限的用户才显示子系统选项（超管隐式拥有） */
const visibleSubsystems = computed(() =>
  userStore.hasPerm('system:subsystem:menu:list') ? subsystems.value : []
)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
  subsystemId: null,
  parentId: 0,
  menuName: '',
  path: '',
  component: '',
  perms: '',
  type: 2,
  icon: '',
  sort: 0,
  visible: 1,
  status: 1
})
const form = reactive(defaultForm())

const formRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

const subsystemDialogVisible = ref(false)
const subsystemFormRef = ref()
const subsystemSubmitting = ref(false)
const subsystemForm = reactive({
  subsystemName: '',
  subsystemCode: '',
  sort: 0,
  status: 1,
  remark: ''
})
const subsystemRules = {
  subsystemName: [{ required: true, message: '请输入子系统名称', trigger: 'blur' }],
  subsystemCode: [
    { required: true, message: '请输入子系统编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]+$/,
      message: '只能包含字母、数字、下划线和短横线',
      trigger: 'blur'
    }
  ]
}

// 上级菜单选项：顶级 + 全部目录/菜单
const parentOptions = computed(() => [
  { id: 0, menuName: '顶级菜单', children: [] },
  ...tableData.value
])

function typeText(type) {
  return { 1: '目录', 2: '菜单', 3: '按钮' }[type] || '-'
}

function typeTag(type) {
  return { 1: 'warning', 2: 'primary', 3: 'info' }[type] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = isSubsystemScope.value
      ? await getSubsystemMenuTree(Number(scope.value))
      : await getMenuTree()
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadSubsystems() {
  const res = await listSubsystems()
  subsystems.value = res.data || []
}

function handleScopeChange() {
  tableData.value = []
  loadData()
}

function openCreateSubsystem() {
  Object.assign(subsystemForm, {
    subsystemName: '',
    subsystemCode: '',
    sort: 0,
    status: 1,
    remark: ''
  })
  subsystemDialogVisible.value = true
}

async function handleCreateSubsystem() {
  try {
    await subsystemFormRef.value.validate()
  } catch (e) {
    return
  }
  subsystemSubmitting.value = true
  try {
    await createSubsystem({ ...subsystemForm })
    ElMessage.success('子系统已新增')
    subsystemDialogVisible.value = false
    await loadSubsystems()
    const created = subsystems.value.find(
      (s) => s.subsystemCode === subsystemForm.subsystemCode
    )
    if (created) {
      // 直接切换到新子系统，方便继续维护它的菜单
      scope.value = String(created.id)
      await loadData()
    }
  } finally {
    subsystemSubmitting.value = false
  }
}

function openCreate(parent) {
  isEdit.value = false
  Object.assign(form, defaultForm())
  form.subsystemId = isSubsystemScope.value ? Number(scope.value) : null
  form.parentId = parent ? parent.id : 0
  // 在目录下新增时默认类型为菜单
  form.type = parent && parent.type === 1 ? 2 : 1
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    subsystemId: isSubsystemScope.value ? Number(scope.value) : null,
    parentId: row.parentId,
    menuName: row.menuName,
    path: row.path,
    component: row.component,
    perms: row.perms,
    type: row.type,
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
    const payload = { ...form }
    if (isSubsystemScope.value) {
      payload.subsystemId = Number(scope.value)
    } else {
      delete payload.subsystemId
    }
    if (isEdit.value) {
      await (isSubsystemScope.value ? updateSubsystemMenu(payload) : updateMenu(payload))
      ElMessage.success('修改成功')
    } else {
      await (isSubsystemScope.value ? createSubsystemMenu(payload) : createMenu(payload))
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
  await (isSubsystemScope.value ? deleteSubsystemMenu(row.id) : deleteMenu(row.id))
  ElMessage.success('删除成功')
  loadData()
}

onMounted(async () => {
  await loadSubsystems()
  await loadData()
})
</script>

<style scoped>
.toolbar-title {
  font-size: 15px;
  font-weight: 500;
}
</style>
