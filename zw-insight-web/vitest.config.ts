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
    // 全局 setup：过滤 Element Plus el-table + happy-dom 已知噪音（2026-08-15 P3 收官）
    setupFiles: ['src/__tests__/setup.ts'],
    include: [
      'src/**/*.test.ts',
      'src/**/*.property.test.ts',
    ],
    // 覆盖率采集（2026-08-14 M3：前端覆盖率度量，基线见 tests/frontend-coverage-baseline.json）
    // 增加 lcov reporter 供 Codecov/GHA 上传
    coverage: {
      provider: 'v8',
      reporter: ['json-summary', 'text', 'lcov'],
      include: ['src/**'],
      exclude: ['src/__tests__/**'],
    },
  },
})
