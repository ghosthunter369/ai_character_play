import { defineConfig } from 'vite'
import * as vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue.default()],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src')
    }
  },
  server: {
    port: 8000,
    proxy: {
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/api')
      }
    }
  }
})