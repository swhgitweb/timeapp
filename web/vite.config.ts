import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本地开发：5173，/api 代理到本地后端 8080（见文档 9.4）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/calendars': 'http://localhost:8080',
    },
  },
})
