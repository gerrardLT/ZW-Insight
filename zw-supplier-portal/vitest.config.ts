import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // 组件测试需解析 .vue SFC（2026-08-15 P3 方向3 门户视图补测）
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
    include: ['tests/**/*.test.ts'],
    // 覆盖率采集（2026-08-14 M3：前端覆盖率度量，基线见 tests/frontend-coverage-baseline.json）
    coverage: {
      provider: 'v8',
      reporter: ['json-summary', 'text'],
      include: ['src/**'],
    },
  }
})
