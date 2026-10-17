import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5177,
    proxy: {
      // 门户自己的接口在 rbac-backend（7070）上 —— 门户不新起后端服务，
      // 它要的三件事（列子系统、换令牌、查登录状态）都挂在 rbac-backend 的 /portal/** 下。
      // 授权端点（授权码跳转）在 rbac-oauth（7071），走整页跳转而非本前端的 axios，不需要代理。
      '/api': {
        target: 'http://localhost:7070',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
