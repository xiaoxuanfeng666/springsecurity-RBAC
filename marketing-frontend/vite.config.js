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
    port: 5175,
    proxy: {
      // 开发环境代理，避免跨域问题。
      // 营销系统的接口全在自己的后端 marketing-backend（7072）上；
      // OAuth2 授权端点（授权码模式跳转）在 rbac-oauth（7071），
      // 走的是整页跳转而非本前端的 axios，不需要代理。
      '/api': {
        target: 'http://localhost:7072',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
