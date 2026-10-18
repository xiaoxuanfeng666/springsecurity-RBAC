import { createRouter, createWebHistory } from 'vue-router'

/**
 * 本工程只服务 OAuth2 授权流程的两个页面，没有登录态与权限体系 ——
 * 流程本身依赖授权服务器（7071）上的 HTTP 会话，与本工程无关。
 *
 * 两个页面都不做路由守卫：能走到这里就说明授权端点已经把人带过来了，
 * 是否需要登录由授权服务器在 /oauth/authorize 上判断并再次 302 到 /login。
 */
const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    // 授权端点在发现未登录时会 302 到这里（见配置项 oauth2.login-page-url）。
    // 登录成功/失败由授权服务器决定跳回哪里，本页只负责收集账号密码。
    path: '/login',
    name: 'OAuthLogin',
    component: () => import('@/views/login/index.vue'),
    meta: { title: 'OAuth2 授权登录' }
  },
  {
    // 外部身份（企业微信等）未绑定时，用本地账号密码自助绑定
    path: '/bind',
    name: 'OAuthBind',
    component: () => import('@/views/bind/index.vue'),
    meta: { title: '绑定系统账号' }
  },
  {
    // 授权确认页：由授权端点 302 跳转过来（见配置项 oauth2.approval-page-url），
    // 此时用户已经在授权服务器上登录过（会话里带着待授权的请求）。
    path: '/approve',
    name: 'OAuthApprove',
    component: () => import('@/views/approve/index.vue'),
    meta: { title: '授权确认' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - OAuth2 授权服务` : 'OAuth2 授权服务'
})

export default router
