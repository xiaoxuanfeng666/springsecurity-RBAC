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
    port: 5174,
    proxy: {
      // 授权服务器端点（/oauth/authorize、/oauth/token、/oauth/login、/oauth/approval-info）。
      // 原样代理、不做路径重写：授权码/隐式模式靠浏览器跳转与原生表单提交，
      // 必须与页面同源，会话 Cookie 才带得上。生产环境同样要求两者同源部署。
      '/oauth': {
        target: 'http://localhost:7071',
        changeOrigin: true
      }
    }
  }
})
