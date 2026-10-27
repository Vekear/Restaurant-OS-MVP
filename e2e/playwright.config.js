/**
 * 餐厅点餐系统 MVP — Playwright 配置（脚手架）
 *
 * 设计要点：
 * - 不使用 webServer 自动拉起：本仓库的 web dev（vite 5173）与 backend（8080）
 *   由 scripts/dev-local.ps1 或开发者手动启动；E2E 复用已运行的服务，避免端口冲突。
 * - baseURL 默认 http://127.0.0.1:5173，可用 E2E_BASE_URL 覆盖（如走 Nginx :80）。
 * - 全程 JavaScript（严禁 TypeScript）；用例放在 tests/*.spec.js。
 * - 产物目录 test-results/、playwright-report/ 已被 .gitignore 忽略。
 *
 * 当前仅含占位用例（tests/placeholder.spec.js，test.skip）；完整业务用例由 T19 补充。
 */

import { defineConfig, devices } from '@playwright/test';

const BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173';
const IS_CI = Boolean(process.env.CI);

export default defineConfig({
  testDir: './tests',
  // 业务链路（下单/支付/接单）存在真实写操作，默认串行，避免同一演示数据互相污染
  fullyParallel: false,
  workers: IS_CI ? 1 : undefined,
  forbidOnly: IS_CI,
  retries: IS_CI ? 1 : 0,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results',
  use: {
    baseURL: BASE_URL,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
