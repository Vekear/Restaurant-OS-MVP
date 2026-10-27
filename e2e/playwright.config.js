/**
 * 餐厅点餐系统 MVP — Playwright E2E 配置（JavaScript）
 *
 * 运行模式：
 * - mock 驱动（默认）：用例通过 e2e/support/api-mocks.js 在浏览器网络层拦截
 *   /api/v1/**，返回严格对齐 docs/02-api.md 的数据，不依赖后端与数据库。
 * - 真后端驱动：`E2E_MOCK=0 npx playwright test`，不注入桩；后端未就绪时
 *   用例会显式 skip（禁止伪造通过）。
 *
 * 服务启动：
 * - 未设置 E2E_BASE_URL 且 E2E_NO_WEBSERVER != 1 时，自动以 webServer 拉起
 *   `web/` 的 vite dev（127.0.0.1:5173，strictPort）；已在运行时直接复用。
 * - 设置 E2E_BASE_URL（如 http://127.0.0.1 走 Nginx）时不启动 webServer，
 *   由 `scripts/dev-local.ps1` 或部署环境统一管理端口。
 * - 可选 E2E_BROWSER_CHANNEL=msedge|chrome 使用本机已安装浏览器（下载受限时兜底）。
 *
 * 其他：baseURL 默认 http://127.0.0.1:5173；全程 JavaScript（严禁 TypeScript）；
 * 产物目录 test-results/、playwright-report/ 已被 .gitignore 忽略。
 */

import { fileURLToPath } from 'node:url';
import { defineConfig, devices } from '@playwright/test';

const BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173';
const IS_CI = Boolean(process.env.CI);
const IS_REAL_MODE = process.env.E2E_MOCK === '0';
const WEB_DIR = fileURLToPath(new URL('../web', import.meta.url));
const USE_WEBSERVER = !process.env.E2E_BASE_URL && process.env.E2E_NO_WEBSERVER !== '1';
const BROWSER_CHANNEL = process.env.E2E_BROWSER_CHANNEL || undefined;

export default defineConfig({
  testDir: './tests',
  // 业务链路（下单/支付/接单）存在真实写操作，默认串行，避免同一演示数据互相污染；
  // 真后端模式再强制 1 worker：并发会触发登录 5 次/分钟限流并互相抢库存，导致假失败。
  fullyParallel: false,
  workers: IS_CI || IS_REAL_MODE ? 1 : undefined,
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
  webServer: USE_WEBSERVER
    ? {
        command: 'npm run dev -- --host 127.0.0.1 --port 5173 --strictPort',
        cwd: WEB_DIR,
        url: 'http://127.0.0.1:5173',
        reuseExistingServer: !IS_CI,
        timeout: 120_000,
      }
    : undefined,
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        ...(BROWSER_CHANNEL ? { channel: BROWSER_CHANNEL } : {}),
      },
    },
  ],
});
