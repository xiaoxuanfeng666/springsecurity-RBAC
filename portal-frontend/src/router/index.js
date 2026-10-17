import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '统一登录' }
  },
  {
    // 授权码回调：授权服务带 ?code=xxx 跳回这里，用 code 换门户令牌
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: () => import('@/views/oauth/callback.vue'),
    meta: { title: '登录中' }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/home/index.vue'),
    meta: { title: '工作台' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 只有「有没有门户令牌」这一层守卫
 * <p>
 * 门户不做权限判定：它只是进各子系统的门，进哪个子系统能不能用是子系统自己的事
 * （权限点由 RBAC 经 MQ 下发给子系统，由子系统拦截）。
 */
router.beforeEach((to) => {
  if (to.path === '/login' || to.path === '/oauth/callback') {
    return true
  }
  if (!getAccessToken()) {
    return { path: '/login' }
  }
  return true
})

export default router
