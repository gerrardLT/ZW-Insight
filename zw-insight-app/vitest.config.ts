import { defineConfig } from 'vitest/config'
import { resolve } from 'node:path'

export default defineConfig({
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    globals: true,
    environment: 'node',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.test.ts', 'tests/**/*.property.test.ts'],
    // 覆盖率采集（2026-08-14 M3：前端覆盖率度量，基线见 tests/frontend-coverage-baseline.json）
    coverage: {
      provider: 'v8',
      reporter: ['json-summary', 'text'],
      include: ['src/**'],
    },
  },
})
