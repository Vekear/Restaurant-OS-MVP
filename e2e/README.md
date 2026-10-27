# E2E（Playwright）— Web 顾客 / 商家 / 管理后台

餐厅点餐系统 MVP 的浏览器端到端测试目录（**JavaScript，严禁 TypeScript**；Playwright 配置为 ESM）。当前共 **38 条用例 / 8 个文件**：

- **mock 驱动（默认，27 条）**：`auth` 8、`menu` 8、`cart` 6、`merchant-orders` 2、`admin-dashboard` 3。在浏览器网络层拦截 `/api/v1/**`，数据严格对齐 `docs/02-api.md`，无需后端与数据库。
- **真实后端（`E2E_MOCK=0`，11 条）**：`real-auth-rbac` 5、`real-file-upload` 2、`real-order-flow` 4。直连本机真实 Spring Boot + MySQL/Redis；后端不可达时整组 `test.skip`，不伪造通过。

## 1. 两种运行模式

| 模式 | 命令 | 依赖 | 说明 |
| --- | --- | --- | --- |
| mock 驱动（默认） | `npm test` | Node + 浏览器 + Web dev | `support/api-mocks.js`、`support/admin-mocks.js`、`merchant-orders.spec.js` 内桩拦截接口；mock 用例自动跳过真实用例 |
| 真后端驱动 | `E2E_MOCK=0 npm test` | Web + Backend(8080) + MySQL/Redis | 不注入桩；只运行 `real-*.spec.js`，mock 用例显式 skip |

mock 模式覆盖页面渲染、表单校验、路由守卫、请求参数、金额/角标联动、空态/错误态、商家订单状态机（有状态桩）、管理后台图表与导出；真实模式覆盖真实登录/RBAC、订单页模拟支付、商家接单→完成、幂等重放、库存不足、文件上传。

## 2. 服务启动（webServer）

- 未设置 `E2E_BASE_URL` 且 `E2E_NO_WEBSERVER != 1` 时，Playwright 自动执行
  `web/` 下的 `npm run dev -- --host 127.0.0.1 --port 5173 --strictPort`，
  就绪后运行用例；已运行的 5173 会被复用（`reuseExistingServer: !CI`）。
- 设置 `E2E_BASE_URL`（例如走 Nginx `http://127.0.0.1`）时不启动 webServer，由
  `scripts/dev-local.ps1` 统一管理端口。
- 直接复用已启动服务：`E2E_NO_WEBSERVER=1 npm test`。
- 真后端模式仍经 Vite 代理转发 `/api/v1`；`E2E_BACKEND_URL` 仅用于测试代码里的 `request` 直连。
- 本机演示环境使用模拟支付与 `storage.type=local`（无 OSS/微信凭证），第三方真实回调不在本 MVP 范围。

## 3. 安装

```bash
cd e2e
npm install                         # 或 pnpm install
npx playwright install chromium     # 首次运行需下载浏览器
```

> 网络受限：`PLAYWRIGHT_DOWNLOAD_HOST=https://npmmirror.com/mirrors/playwright npx playwright install chromium`。
> 仍失败可复用本机 Chrome/Edge：`E2E_BROWSER_CHANNEL=chrome npm test`（或 `msedge`）。

## 4. 运行

```bash
cd e2e
npm test                  # mock 全量（27 passed / 11 skipped）
npm run test:smoke        # auth + menu（16 条）
npm run test:auth         # 登录 / 注册（8 条）
npm run test:cart         # 购物车（6 条）
npm run test:merchant     # 商家订单看板（2 条，mock 状态机）
npm run test:admin        # 管理后台（3 条，mock）
npm run test:headed       # 有界面
npm run test:ui           # Playwright UI（调试）
npm run list              # 只列出用例（38 tests / 8 files）

E2E_BASE_URL=http://127.0.0.1 npm test          # 走 Nginx 网关
E2E_MOCK=0 npm test                             # 真后端全量（写数据，需 Lead 许可）
E2E_MOCK=0 npx playwright test tests/real-auth-rbac.spec.js
```

- 报告与产物：`e2e/playwright-report/`（HTML）、`e2e/test-results/`（trace / 失败截图 / 视频）。
- 根 `.gitignore` 已覆盖 `e2e/test-results/`、`e2e/playwright-report/`、`e2e/blob-report/`、`e2e/.cache/`；`e2e/.gitignore` 为补充。
- CI 下自动 `retries=1`、`workers=1`、`forbidOnly`；本地默认串行（`fullyParallel: false`）。

## 5. 目录结构与用例清单

```text
e2e/
├── playwright.config.js          # baseURL / webServer / channel / 报告
├── support/
│   ├── api-mocks.js              # 顾客端契约夹具 + 接口路由 + 请求记录 + 会话注入
│   ├── admin-mocks.js            # /admin/** 桩（指标、趋势、订单、审计、xlsx）
│   └── real-stack.js             # 真实后端助手（登录/token 缓存、下单/支付/流转、上传、会话注入）
├── tests/
│   ├── auth.spec.js              # 登录/注册、校验、守卫、401 提示、会话持久化
│   ├── menu.spec.js              # 分类/菜品、搜索、排序、翻页、空态/错误态、游客加购
│   ├── cart.spec.js              # 空态、加购角标、增减、删除、失效行、清空
│   ├── merchant-orders.spec.js   # T28 回归 + 外卖状态机（接单→制作→配送→完成）+ 拒单
│   ├── admin-dashboard.spec.js   # 管理后台图表、导出、审计
│   ├── real-auth-rbac.spec.js    # 真实登录/RBAC、JWT 篡改、403
│   ├── real-order-flow.spec.js   # 真实支付 UI、商家状态机、幂等、库存不足/回补
│   └── real-file-upload.spec.js  # 真实 policy/upload/回读、未登录 401
├── package.json
└── README.md
```

| 文件 | 用例数 | 关键断言 |
| --- | --- | --- |
| `tests/auth.spec.js` | 8 | 表单必填/长度/确认密码/手机号；`/cart`、`/orders`、`/checkout` 未登录重定向带 `redirect`；错误口令 401 提示且不写 token；登录成功写 `restaurant.token` 并进入 `/`；登录后回跳原页面 |
| `tests/menu.spec.js` | 8 | 首屏 12 卡片 / 共 26 道菜；`merchantId=1&sort=default&page=1&size=12`；搜索 `keyword=宫保`；分类 `categoryId=1/2`；排序 `price_desc` 首项南瓜粥；翻页 `page=2`；空态「暂无数据」；错误态「加载失败」→ 重新加载恢复；游客加购提示登录且无写请求 |
| `tests/cart.spec.js` | 6 | 空购物车态；加购后头部角标 1→2、`POST /cart/items` 请求体、合计 59.00；增减数量 `PATCH` 后金额 87.00/59.00 与角标 3/2；删除 `DELETE` 后 28.00；失效行不计入合计并显示警告；清空确认后回空态 |
| `tests/merchant-orders.spec.js` | 2 | 待接单「接单/拒单」`disabled=false`（T28 回归）；`PAID → ACCEPTED → MAKING → DELIVERING → COMPLETED` 全链路（含确认框）；拒单理由 2–100 字校验；完成后回到「该状态下暂无订单」 |
| `tests/admin-dashboard.spec.js` | 3 | 平台概览 7 指标卡 + 3 ECharts 画布 + 导出请求；数据统计 7 概览卡 + 3 图；订单导出与审计日志渲染 |
| `tests/real-auth-rbac.spec.js` | 5 | 顾客 UI 登录持久化；未登录 `/cart` → `/login?redirect=/cart`；顾客/商家访问 `/admin/dashboard` → `/403`；商家真实订单列表；无 token/篡改 JWT → 401，顾客调商家接口 → 403 |
| `tests/real-order-flow.spec.js` | 4 | 顾客订单页模拟支付 ¥0.01 → PAID；商家看板接单→制作→配送→完成；同一 Idempotency-Key 重放同订单；库存不足 40901 + 取消回补 |
| `tests/real-file-upload.spec.js` | 2 | `bizType=dish&ext=png` policy → multipart 上传 → `/files/...` 回读 200 `image/png`；未登录上传 401 |

## 6. mock 契约与真实栈助手

`e2e/support/api-mocks.js`：

- 夹具：`PUBLIC_CONFIG`、`createCategoryFixtures()`（热菜 18 / 主食 8）、`createDishFixtures()`（26 道，含「酸辣土豆丝」售罄）、`makeCartItem()`、`cartViewFromItems()`（`totalAmount` 仅计可售行、`invalidCount` 统计不可售行）。
- 工具：`installApiMocks(page, options)`（`/api/v1/**` 路由 + `/files/**` 占位图 + WebSocket 桩）、`collectApiRequests(page)`、`seedSession(page)`、`expectToast()`、`loginViaUi()`、`isRealMode()` / `isRealBackendReachable()`。

`e2e/support/real-stack.js`：

- `apiLogin(request, username, password)`：token 缓存 `e2e/.cache/e2e-tokens.json`（30 分钟、`/auth/me` 校验、429 退避），规避登录 5 次/分/IP 限流。
- `createOrder` / `getOrder` / `payOrder` / `merchantAction` / `uploadPng` / `fetchMerchantStock` / `pickSellableDish`。
- `seedRealSession(page, { token, user })` / `loginViaRealUi(page)`。

新增接口桩时以 `docs/02-api.md` 字段为准，禁止在 `web/` 源码中注入 mock 数据。

## 7. 约定

1. 文件名 `tests/<主题>.spec.js`；共享步骤放 `support/`。
2. 断言以用户可见文案、路由与请求参数为主；后端安全细节（RBAC、幂等、验签、并发库存）由后端集成测试与安全探测覆盖。
3. 不引入 TypeScript；不提交 `test-results/`、`playwright-report/`、trace、截图等产物。
4. 真实用例只允许在隔离环境或 Lead 明确许可的窗口内执行；不得把桩或测试服务绑定到 8080。
5. 真后端模式下依赖预置写数据/未实现接口的用例会显式 `skip` 并标注原因，不允许静默失败或伪造通过。

## 8. 已验证 / 未验证

已验证（2026-10-28 本机）：

- `npx playwright install chromium`（经 npmmirror 镜像）→ `chromium-1248`、`chromium_headless_shell-1248`、`ffmpeg-1013`、`winldd-1007`，exit 0。
- `npx playwright test --list` → **Total: 38 tests in 8 files**，exit 0。
- `npx playwright test`（mock）→ **27 passed / 11 skipped (11.3s)**，exit 0。
- `npx playwright test tests/merchant-orders.spec.js` → **2 passed (4.2s)**（T28 回归 + 状态机）。
- `E2E_MOCK=0 npx playwright test tests/real-auth-rbac.spec.js` → 5 passed。
- `E2E_MOCK=0 npx playwright test tests/real-file-upload.spec.js` → 2 passed。
- `E2E_MOCK=0 npx playwright test tests/real-order-flow.spec.js`（T28 修复前）→ 2 passed / 2 failed（商家按钮 disabled、库存断言口径）；已用只读 DOM 探针确认修复后按钮可点，真实点击链路待 Go 后重跑。
- 后端不可达时 `E2E_MOCK=0 npx playwright test` → 11 skipped，exit 0。

未验证：

- T28 修复后的真实后端商家状态机点击链路与库存用例（写链路暂停，待「T19 real-backend Go」）。
- 真实堂食扫码/桌台绑定/会话释放、购物车/地址/优惠券页面、管理后台真实接口与 xlsx 打开、WebSocket 推送到达浏览器、OSS 直传、微信真机与小程序 E2E、Nginx/Docker 运行态。

## 9. 故障排查

| 现象 | 处理 |
| --- | --- |
| `net::ERR_CONNECTION_REFUSED` | webServer 未就绪或被 `E2E_NO_WEBSERVER=1` 关闭；检查 5173 是否监听 |
| `Executable doesn't exist ... chromium` | `npx playwright install chromium`，或临时 `E2E_BROWSER_CHANNEL=chrome` |
| 登录 401/500 或 429（真后端模式） | 检查后端迁移/种子数据与 MySQL；`curl http://127.0.0.1:8080/actuator/health`；登录限流 5 次/分/IP，等 60s 或复用 token 缓存 |
| 用例被 skip | 真后端模式不可达；或 mock 用例在 `E2E_MOCK=0` 下按设计跳过 |
| 端口被占用 | 5173/8080 被占用时先 `pnpm dev:stop` 或改用 `E2E_BASE_URL` |
| 产物污染工作区 | 产物已被 `.gitignore` 忽略；清理：`rm -rf test-results playwright-report` |
