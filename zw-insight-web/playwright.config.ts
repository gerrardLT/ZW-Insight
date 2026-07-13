import { defineConfig, devices } from '@playwright/test'

/**
 * 真实模式 baseURL：优先读取环境变量 E2E_SERVER_URL，
 * 默认指向部署服务器前端地址（Nginx :18081）
 */
const realBaseURL = process.env.E2E_SERVER_URL || 'http://129.204.3.200:18081'

export default defineConfig({
  testDir: './e2e/tests',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: true,
  workers: 4,
  retries: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:3000',
    headless: true,
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    // 使用系统 Chrome，无需下载 Chromium
    ...devices['Desktop Chrome'],
    channel: 'chrome',
    storageState: './e2e/.auth/storage-state.json',
  },
  // 全局 setup：执行登录并保存 storageState
  projects: [
    // ─── Mock 模式（本地开发 / UI 回归） ───
    {
      name: 'setup',
      testDir: './e2e/fixtures',
      testMatch: /auth\.setup\.ts/,
      use: { storageState: { cookies: [], origins: [] } },
    },
    {
      name: 'e2e',
      dependencies: ['setup'],
      testDir: './e2e/tests',
    },

    // ─── 真实模式（打服务器 :18081） ───
    {
      name: 'setup-real',
      testDir: './e2e/fixtures',
      testMatch: /auth-real\.setup\.ts/,
      use: {
        baseURL: realBaseURL,
        storageState: { cookies: [], origins: [] },
      },
    },
    {
      name: 'e2e-real',
      dependencies: ['setup-real'],
      testDir: './e2e/tests/real',
      use: {
        baseURL: realBaseURL,
        storageState: './e2e/.auth/storage-state.json',
      },
      workers: 4,
    },
  ],
})
