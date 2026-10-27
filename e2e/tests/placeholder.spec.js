/**
 * E2E 占位用例（脚手架，不访问任何服务）。
 *
 * T19 将在此目录补充完整链路用例（登录/RBAC、顾客下单与模拟支付、商家接单→
 * 制作→配送/完成、桌台扫码会话、管理后台报表与导出等）。在此之前保持 test.skip，
 * 保证 `playwright test --list` 可运行且不会误报通过。
 */

import { test } from '@playwright/test';

test.skip(
  'T19 将补充完整 E2E 用例（当前为脚手架占位，不访问 Web/后端）',
  async () => {
    // 故意留空：T19 用真实用例替换本文件。
  },
);
