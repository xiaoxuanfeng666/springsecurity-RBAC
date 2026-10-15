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
      // 开发环境代理，避免跨域问题
      '/api': {
        target: 'http://localhost:7070',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // OAuth2 授权端点：授权码/隐式模式由浏览器直接跳转与表单提交，
      // 必须与前端同源（会话 Cookie 才能带上），因此原样代理、不做路径重写
      '/oauth': {
        target: 'http://localhost:7070',
        changeOrigin: true
      }
    }
  }
})
