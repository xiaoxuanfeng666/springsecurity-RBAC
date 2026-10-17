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
    port: 5178,
    proxy: {
      // 开发环境代理，避免跨域问题。
      // 审计日志系统的接口全在自己的后端 audit-backend（7074）上；
      // OAuth2 授权端点（授权码模式跳转）在 rbac-oauth（7071），
      // 走的是整页跳转而非本前端的 axios，不需要代理。
      '/api': {
        target: 'http://localhost:7074',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
