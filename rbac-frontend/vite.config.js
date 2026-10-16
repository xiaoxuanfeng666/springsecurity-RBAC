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
    port: 5173,
    proxy: {
      // 开发环境代理，避免跨域问题。
      // 后端接口（含「客户端管理」）都在 rbac-backend；OAuth2 授权端点与授权流程页面
      // 分别在 rbac-oauth（7071）与 rbac-oauth-web（5174），与本后台无关，故这里不再代理 /oauth。
      '/api': {
        target: 'http://localhost:7070',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
