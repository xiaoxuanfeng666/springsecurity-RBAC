import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'

const staticRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    // OAuth2 授权确认页：由后端授权端点 302 跳转过来，此时前端并没有 JWT 登录态，
    // 因此必须免登录可访问（见下面 PUBLIC_PATHS）。
    //
    // 【路径不能放在 /oauth 下】后端保留了 /oauth/** 前缀，开发环境的 Vite 代理
    // 与生产环境的 Nginx 都会把该前缀反代给后端。放在这里（如 /oauth/approve）
    // 会被直接转给后端，后端没有这个映射，浏览器只会看到 404，前端页面永远加载不出来。
    path: '/approve',
    name: 'OAuthApprove',
    component: () => import('@/views/oauth/approve.vue'),
    meta: { title: '授权确认' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]

/** 免登录即可访问的路径：登录页，以及 OAuth2 授权流程中的确认页 */
const PUBLIC_PATHS = ['/login', '/approve']

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
