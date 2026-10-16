import { defineStore } from 'pinia'
import Layout from '@/layout/index.vue'

// 预加载所有页面组件，用于按后端菜单的 component 字段动态解析
const viewModules = import.meta.glob('../views/**/*.vue')

function resolveComponent(component) {
  if (!component) {
    return () => import('@/views/error/404.vue')
  }
  const key = `../views/${component}.vue`
  return viewModules[key] || (() => import('@/views/error/404.vue'))
}

/**
 * 根据后端菜单树构造动态路由
 * - type=1 目录 -> Layout + 子菜单
 * - type=2 顶级菜单 -> Layout 下单子路由（如仪表盘）
 */
function buildRoutes(menus) {
  const routes = []
  const topLevelMenus = []

  menus.forEach((menu) => {
    if (menu.type === 1) {
      const children = (menu.children || [])
        .filter((c) => c.type === 2)
        .map((c) => ({
          path: c.path,
          name: 'Menu' + c.id,
          component: resolveComponent(c.component),
          meta: { title: c.menuName, icon: c.icon }
        }))
      if (children.length === 0) {
        return
      }
      routes.push({
        path: menu.path,
        name: 'Dir' + menu.id,
        component: Layout,
        meta: { title: menu.menuName, icon: menu.icon },
        children
      })
    } else if (menu.type === 2) {
      topLevelMenus.push(menu)
    }
  })

  if (topLevelMenus.length > 0) {
    routes.push({
      path: '/',
      name: 'TopLevel',
      component: Layout,
      children: topLevelMenus.map((m) => ({
        path: m.path.replace(/^\//, ''),
        name: 'Menu' + m.id,
        component: resolveComponent(m.component),
        meta: { title: m.menuName, icon: m.icon }
      }))
    })
  }

  return routes
}

/** 找到第一个可访问的页面路径，用于登录后跳转 */
function findFirstPath(routes) {
  for (const route of routes) {
    if (route.children && route.children.length > 0) {
      const base = route.path === '/' ? '' : route.path
      const child = route.children[0]
      const full = `${base}/${child.path}`.replace(/\/{2,}/g, '/')
      return full.startsWith('/') ? full : '/' + full
    }
  }
  return '/404'
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    /** 侧边栏菜单树 */
    menus: [],
    /** 动态路由 */
    dynamicRoutes: [],
    /** 是否已生成过路由 */
    routesGenerated: false,
    /** 登录后的默认落地页 */
    defaultPath: '/404'
  }),

  actions: {
    generateRoutes(menus) {
      this.menus = menus || []
      const routes = buildRoutes(this.menus)
      this.dynamicRoutes = routes
      this.defaultPath = findFirstPath(routes)
      this.routesGenerated = true
      return routes
    },

    reset() {
      this.menus = []
      this.dynamicRoutes = []
      this.routesGenerated = false
      this.defaultPath = '/404'
    }
  }
})
