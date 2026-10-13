<template>
  <template v-for="menu in visibleMenus" :key="menu.id">
    <!-- 目录：仅在有可见子菜单时渲染 -->
    <el-sub-menu v-if="menu.type === 1 && childMenus(menu).length > 0" :index="resolvePath(menu.path)">
      <template #title>
        <el-icon v-if="menu.icon">
          <component :is="menu.icon" />
        </el-icon>
        <span>{{ menu.menuName }}</span>
      </template>
      <sidebar-item :menus="childMenus(menu)" :base-path="menu.path" />
    </el-sub-menu>

    <!-- 菜单 -->
    <el-menu-item v-else-if="menu.type === 2" :index="resolvePath(menu.path)">
      <el-icon v-if="menu.icon">
        <component :is="menu.icon" />
      </el-icon>
      <template #title>{{ menu.menuName }}</template>
    </el-menu-item>
  </template>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  menus: {
    type: Array,
    default: () => []
  },
  basePath: {
    type: String,
    default: ''
  }
})

const visibleMenus = computed(() => (props.menus || []).filter((m) => m.visible !== 0))

function childMenus(menu) {
  return (menu.children || []).filter((c) => c.type === 2 && c.visible !== 0)
}

function resolvePath(path) {
  if (!path) {
    return ''
  }
  if (path.startsWith('/')) {
    return path
  }
  return `${props.basePath}/${path}`.replace(/\/{2,}/g, '/')
}
</script>
