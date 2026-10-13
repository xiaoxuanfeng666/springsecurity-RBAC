<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">RBAC 权限管理</div>
      <el-menu
        :default-active="activeMenu"
        router
        unique-opened
        background-color="#001529"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <sidebar-item :menus="permissionStore.menus" />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><user-filled /></el-icon>
            <span class="nickname">{{ displayName }}</span>
            <el-icon><arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><switch-button /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import SidebarItem from './components/SidebarItem.vue'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'
import { resetDynamicRoutes } from '@/router'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

const activeMenu = computed(() => route.path)

const currentTitle = computed(() => route.meta?.title || '')

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '未登录'
)

async function handleCommand(command) {
  if (command !== 'logout') {
    return
  }
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await userStore.logout()
  resetDynamicRoutes()
  permissionStore.reset()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background-color: #001529;
  overflow-x: hidden;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  background-color: #002140;
}

.aside :deep(.el-menu) {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
}

.header-title {
  font-size: 16px;
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
}

.nickname {
  font-size: 14px;
}

.main {
  background-color: #f0f2f5;
  padding: 0;
}
</style>
