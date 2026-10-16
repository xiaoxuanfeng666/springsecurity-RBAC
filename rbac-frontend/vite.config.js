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
      // 客户端管理（OAuth2 接入方配置）随授权服务器拆到了 rbac-oauth。
      // 必须声明在 '/api' 之前：代理按键的声明顺序匹配，'/api' 若在前会把这条一并吃掉。
      '/api/client': {
        target: 'http://localhost:7071',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 本系统自身的业务接口仍在 rbac-backend
      '/api': {
        target: 'http://localhost:7070',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
