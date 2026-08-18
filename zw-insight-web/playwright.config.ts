import { defineConfig, devices } from '@playwright/test'

/**
 * 真实模式 baseURL：优先读取环境变量 E2E_SERVER_URL，
 * 默认指向部署服务器前端地址（Nginx :18081）
 *
 * 注：Mock 模式（假 token + route() 拦截假数据）已于 2026-08-11 归档删除，
 * 违反「真实接口真实流程」原则且与 real 模式冗余；UI 回归统一走真实模式
 */
const realBaseURL = process.env.E2E_SERVER_URL || 'http://129.204.3.200:18081'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: true,
  workers: 4,
  retries: 1,
  reporter: 'list',
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    // 使用系统 Chrome，无需下载 Chromium
    ...devices['Desktop Chrome'],
    channel: 'chrome',
    storageState: './e2e/.auth/storage-state.json',
  },
  // 全局 setup：执行真实登录并保存 storageState
  projects: [
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

    // ─── 一致性模式（前端展示 vs 后端数据 字段级比对，打服务器 :18081） ───
    {
      name: 'consistency-real',
      dependencies: ['setup-real'],
      testDir: './e2e/consistency',
      testMatch: /.*\.spec\.ts$/,
      use: {
        baseURL: realBaseURL,
        storageState: './e2e/.auth/storage-state.json',
      },
      // 顺序执行，避免并发写库；一致性用例以只读比对为主
      workers: 2,
    },

    // ─── 视觉回归模式（W4：真实服务器关键页面截图基线比对） ───
    // 基线生成：npm run test:e2e:visual:update（首次或 UI 有意变更时）
    // 日常验证：npm run test:e2e:visual（与 e2e/visual-snapshots/ 下基线比对）
    {
      name: 'visual-real',
      dependencies: ['setup-real'],
      testDir: './e2e/visual',
      testMatch: /.*\.spec\.ts$/,
      snapshotPathTemplate: '{testDir}/../visual-snapshots/{arg}{ext}',
      use: {
        baseURL: realBaseURL,
        storageState: './e2e/.auth/storage-state.json',
      },
      expect: {
        toHaveScreenshot: {
          // 真实服务器演示数据存在微量动态差异（时间戳残影/图表抗锯齿），
          // 以像素比例兜底；结构性回归（布局塌陷/组件丢失）远超该阈值必 FAIL
          maxDiffPixelRatio: 0.02,
          animations: 'disabled',
          caret: 'hide',
        },
      },
      // 单 worker 串行：避免并发截图时服务器负载差异引起渲染抖动
      workers: 1,
      retries: 0,
    },
  ],
})
