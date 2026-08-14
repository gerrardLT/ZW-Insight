import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig({
  // 组件测试需解析 .vue SFC（2026-08-14 前端深度补测）
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    globals: true,
    // happy-dom：stores 依赖 localStorage、组件测试依赖 DOM（2026-08-14 前端深度补测）
    environment: 'happy-dom',
    include: [
      'src/**/*.test.ts',
      'src/**/*.property.test.ts',
    ],
  },
})
