import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // H5 devServer 代理：request.ts BASE_URL='/api' 直打真实联调服务器（2026-08-28 迁移 Phase 1，验证阶段前提）
  server: {
    proxy: {
      '/api': {
        target: 'http://129.204.3.200:18080',
        changeOrigin: true
      }
    }
  }
})
