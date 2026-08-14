import { defineConfig } from 'vitest/config'

export default defineConfig({
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
