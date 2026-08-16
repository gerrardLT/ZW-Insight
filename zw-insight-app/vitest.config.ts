import { defineConfig } from 'vitest/config'
import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'

/**
 * uni-app 内置组件白名单（2026-08-16 P3 方向2 app 页面级测试）：
 * 组件测试用标准 @vitejs/plugin-vue 编译 .vue，uni 专有标签声明为
 * custom element 使其在 happy-dom 下直接渲染为原生未知元素。
 */
const UNI_TAGS = new Set([
  'view', 'text', 'scroll-view', 'swiper', 'swiper-item', 'navigator',
  'image', 'icon', 'progress', 'rich-text', 'cover-view', 'cover-image',
  'movable-area', 'movable-view', 'web-view', 'ad',
])

export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: {
          isCustomElement: (tag) => UNI_TAGS.has(tag),
        },
      },
    }),
  ],
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
