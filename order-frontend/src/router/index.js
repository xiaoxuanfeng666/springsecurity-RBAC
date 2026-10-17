import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'

const staticRoutes = [
  {
    // 本系统没有登录页：这个路由是「去统一门户登录」的跳转页。
    // 路由守卫、401 拦截器、登出回跳都指向 /login，保留这个路径它们就都不用改。
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '统一登录' }
  },
  {
    // 授权码模式的回调页：这一刻还没有令牌，必须放在免登录名单里
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: () => import('@/views/oauth/callback.vue'),
    meta: { title: '授权登录' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]

/** 免登录即可访问的路径：登录页与授权码回调页 */
const PUBLIC_PATHS = ['/login', '/oauth/callback']

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
  scrollBehavior: () => ({ top: 0 })
})

// 动态路由注册标记，用于登出后重置
let dynamicRouteNames = []

function resetDynamicRoutes() {
  dynamicRouteNames.forEach((name) => {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  })
  dynamicRouteNames = []
}

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  if (PUBLIC_PATHS.includes(to.path)) {
    next()
    return
  }

  if (!userStore.accessToken) {
    next('/login')
    return
  }

  if (permissionStore.routesGenerated) {
    next()
    return
  }

  // 已有 token 但未加载用户信息与动态路由
  try {
    const info = await userStore.getInfo()
    const routes = permissionStore.generateRoutes(info.menus)
    routes.forEach((route) => {
      router.addRoute(route)
      if (route.name) {
        dynamicRouteNames.push(route.name)
      }
    })
    // 兜底 404，必须最后添加
    router.addRoute({ path: '/:pathMatch(.*)*', redirect: '/404' })

    // 重定向到根路径时，落到第一个可访问页面
    if (to.path === '/' || to.path === '') {
      next({ path: permissionStore.defaultPath, replace: true })
    } else {
      next({ ...to, replace: true })
    }
  } catch (e) {
    resetDynamicRoutes()
    userStore.resetState()
    permissionStore.reset()
    next('/login')
  }
})

export { resetDynamicRoutes }
export default router
