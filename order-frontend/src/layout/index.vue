<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">订单中心</div>
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
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import SidebarItem from './components/SidebarItem.vue'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'
import { resetDynamicRoutes } from '@/router'
import { ssoLogout } from '@/utils/sso'
import { startSessionWatch, stopSessionWatch } from '@/utils/sessionWatch'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

// 本组件只在已登录时渲染，因此在这里挂上「切回标签页校验登录态」最合适：
// 在别的子系统登出后，用户切回来的那一刻就会被踢到登录页，而不是等他点下一个按钮。
onMounted(startSessionWatch)
onUnmounted(stopSessionWatch)

const activeMenu = computed(() => route.path)

const currentTitle = computed(() => route.meta?.title || '')

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '未登录'
)

/**
 * 只有退出登录：本系统没有自己的账号体系，改密码、改资料都归 RBAC 管
 */
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
  // 统一登出：先带令牌让授权服务删掉 SSO 会话（其他子系统的令牌立刻失效），
  // 再整页跳过去销毁浏览器会话，最后回到登录页。跳转成功时不会返回，也不会执行下面的 router.push。
  const navigated = await ssoLogout()
  userStore.resetState()
  resetDynamicRoutes()
  permissionStore.reset()
  if (!navigated) {
    router.push('/login')
  }
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
