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
    // 覆盖率采集（2026-08-14 M3：前端覆盖率度量，基线见 tests/frontend-coverage-baseline.json）
    coverage: {
      provider: 'v8',
      reporter: ['json-summary', 'text'],
      include: ['src/**'],
      exclude: ['src/__tests__/**'],
    },
  },
})
