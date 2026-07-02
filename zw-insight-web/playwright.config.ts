import { defineConfig, devices } from '@playwright/test'

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
  ],
})
