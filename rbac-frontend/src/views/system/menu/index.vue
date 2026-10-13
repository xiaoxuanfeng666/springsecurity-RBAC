<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="toolbar-title">菜单与权限</span>
        <div>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          <el-button v-permission="'system:menu:add'" type="primary" :icon="Plus" @click="openCreate(null)">
            新增根菜单
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import { getMenuTree, createMenu, updateMenu, deleteMenu } from '@/api/menu'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const defaultForm = () => ({
  id: null,
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
    const res = await getMenuTree()
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate(parent) {
  isEdit.value = false
  Object.assign(form, defaultForm())
  form.parentId = parent ? parent.id : 0
  // 在目录下新增时默认类型为菜单
  form.type = parent && parent.type === 1 ? 2 : 1
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
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
    if (isEdit.value) {
      await updateMenu({ ...form })
      ElMessage.success('修改成功')
    } else {
      await createMenu({ ...form })
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
  await deleteMenu(row.id)
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
