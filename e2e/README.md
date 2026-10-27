# E2E（Playwright）— Web 登录 / 菜单 / 购物车

餐厅点餐系统 MVP 的浏览器端到端测试目录。**当前包含 T25 交付的 22 条用例**（登录与注册 8 条、菜单 8 条、购物车 6 条），全部为 **mock 驱动**：在浏览器网络层拦截 `/api/v1/**`，返回严格对齐 `docs/02-api.md` 的数据，无需后端与数据库即可运行；预留 `E2E_MOCK=0` 真后端模式复用同一套用例。

> 全目录使用 **JavaScript**（`.js` / `.mjs`），严禁 TypeScript；Playwright 配置使用 ESM（`"type": "module"`）。

## 1. 两种运行模式

| 模式 | 命令 | 依赖 | 说明 |
| --- | --- | --- | --- |
| mock 驱动（默认） | `npm test` | Node + 浏览器 + Web dev | `e2e/support/api-mocks.js` 拦截接口；Web 由 `webServer` 自动拉起（见下） |
| 真后端驱动 | `E2E_MOCK=0 npm test` | Web + Backend(8080) + MySQL/Redis | 不注入桩；`GET /api/v1/public/config` 不可达时整组 `test.skip`（不伪造通过） |

mock 模式覆盖页面渲染、表单校验、路由守卫、请求参数、金额/角标联动与空态/错误态；**真实后端联调**（登录 401/403、库存并发、支付回调等）仍由后端集成测试与 T19 的全量 E2E 覆盖。

## 2. 服务启动（webServer）

- 未设置 `E2E_BASE_URL` 且 `E2E_NO_WEBSERVER != 1` 时，Playwright 自动执行
  `web/` 下的 `npm run dev -- --host 127.0.0.1 --port 5173 --strictPort`，
  就绪后运行用例；已运行的 5173 会被复用（`reuseExistingServer: !CI`）。
- 设置 `E2E_BASE_URL`（例如走 Nginx `http://127.0.0.1`）时不启动 webServer，由
  `scripts/dev-local.ps1` 统一管理端口。
- 直接复用已启动服务：`E2E_NO_WEBSERVER=1 npm test`。
- 本机演示环境使用模拟支付与 `storage.type=local`（无 OSS/微信凭证），涉及真实第三方回调的场景不在本 MVP 范围。

## 3. 安装

```bash
cd e2e
npm install                         # 或 pnpm install
npx playwright install chromium     # 首次运行需下载浏览器
```

> 网络受限时可用 `PLAYWRIGHT_DOWNLOAD_HOST=https://npmmirror.com/mirrors/playwright npx playwright install chromium`。
> 若仍无法下载，可复用本机已安装的 Chrome/Edge：`E2E_BROWSER_CHANNEL=chrome npm test`（或 `msedge`）。

## 4. 运行

```bash
cd e2e
npm test                  # 全部 22 条（mock 驱动，串行）
npm run test:smoke        # auth + menu（16 条）
npm run test:auth         # 登录 / 注册（8 条）
npm run test:cart         # 购物车（6 条）
npm run test:headed       # 有界面
npm run test:ui           # Playwright UI（调试）
npm run list              # 只列出用例，不执行

E2E_BASE_URL=http://127.0.0.1 npm test   # 走 Nginx 网关
E2E_MOCK=0 npm test                      # 真后端模式（后端未就绪时显式 skip）
```

- 报告与产物：`e2e/playwright-report/`（HTML）、`e2e/test-results/`（trace / 失败截图 / 视频）。
- 根 `.gitignore` 已覆盖 `e2e/test-results/`、`e2e/playwright-report/`、`e2e/blob-report/`、`e2e/.cache/`；`e2e/.gitignore` 为补充。
- CI 下自动 `retries=1`、`workers=1`、`forbidOnly`；本地默认串行（`fullyParallel: false`）。

## 5. 目录结构与用例清单

```text
e2e/
├── playwright.config.js        # baseURL / webServer / 浏览器 channel / 报告
├── support/
│   └── api-mocks.js            # 契约夹具 + 接口路由 + 请求记录 + 会话注入
├── tests/
│   ├── auth.spec.js            # 登录页、注册页、校验、守卫、401 提示、登录成功
│   ├── menu.spec.js            # 分类/菜品、搜索、排序、翻页、空态/错误态、游客加购
│   └── cart.spec.js            # 空态、加购角标、增减、删除、失效行、清空
├── package.json
└── README.md
```

| 文件 | 用例数 | 关键断言 |
| --- | --- | --- |
| `tests/auth.spec.js` | 8 | 表单必填/长度/确认密码/手机号；`/cart`、`/orders`、`/checkout` 未登录重定向带 `redirect`；错误口令 401 提示且不写 token；登录成功写 `restaurant.token` 并进入 `/`；登录后回跳原页面 |
| `tests/menu.spec.js` | 8 | 首屏 12 卡片 / 共 26 道菜；`merchantId=1&sort=default&page=1&size=12`；搜索 `keyword=宫保`；分类 `categoryId=1/2`；排序 `price_desc` 首项南瓜粥；翻页 `page=2`；空态「暂无数据」；错误态「加载失败」→ 重新加载恢复；游客加购提示登录且无写请求 |
| `tests/cart.spec.js` | 6 | 空购物车态；加购后头部角标 1→2、`POST /cart/items` 请求体、合计 59.00；增减数量 `PATCH` 后金额 87.00/59.00 与角标 3/2；删除 `DELETE` 后 28.00；失效行不计入合计并显示警告；清空确认后回空态 |

### mock 数据契约

`e2e/support/api-mocks.js` 导出：

- 夹具：`PUBLIC_CONFIG`、`createCategoryFixtures()`（热菜 18 / 主食 8）、`createDishFixtures()`（26 道，含「酸辣土豆丝」售罄）、`makeCartItem()`、`cartViewFromItems()`（`totalAmount` 仅计可售行、`invalidCount` 统计不可售行）。
- 工具：`installApiMocks(page, options)`（`/api/v1/**` 路由 + `/files/**` 占位图 + WebSocket 桩）、`collectApiRequests(page)`（`filter/last` 记录 method/path/query/body）、`seedSession(page)`（localStorage 注入 `restaurant.token` / `restaurant.user`）、`expectToast()`、`loginViaUi()`、`isRealMode()` / `isRealBackendReachable()`。

新增接口桩时以 `docs/02-api.md` 字段为准，禁止在 `web/` 源码中注入 mock 数据。

## 6. 约定

1. 文件名 `tests/<主题>.spec.js`；共享步骤放 `support/`。
2. 断言以用户可见文案、路由与请求参数为主；后端安全细节（RBAC、幂等、验签、并发库存）由后端集成测试覆盖。
3. 不引入 TypeScript；不提交 `test-results/`、`playwright-report/`、trace、截图等产物。
4. 真后端模式下只读断言可直接运行，依赖预置写数据的用例会显式 `skip` 并标注原因。

## 7. 已验证 / 未验证

已验证（2026-10-28，本机，mock 模式）：

- `npx playwright install chromium`（经 `PLAYWRIGHT_DOWNLOAD_HOST=https://npmmirror.com/mirrors/playwright` 镜像重试）→ 安装成功，`%LOCALAPPDATA%/ms-playwright` 下含 `chromium-1248`、`chromium_headless_shell-1248`、`ffmpeg-1013`、`winldd-1007`，exit code `0`。
- `npx playwright test --list` → **Total: 22 tests in 3 files**，exit code `0`。
- `npx playwright test`（默认 Playwright chromium）→ **22 passed (10.5s)**，exit code `0`。
- `E2E_BROWSER_CHANNEL=chrome npx playwright test` → 22 passed (13.3s)，exit code `0`（本机 Chrome 兜底路径同样可用）。
- 后端未启动时 `E2E_MOCK=0 npx playwright test` → **22 skipped**，exit code `0`（按设计显式跳过，不伪造通过）。

未验证：

- 真后端驱动模式（`E2E_MOCK=0`）的真实接口联调：登录/菜单/购物车的真实数据、种子数据依赖与写操作清理；本次仅验证后端不可达时的 skip 行为。
- 商家/管理后台、下单/支付/接单/报表、小程序与微信开发者工具真机链路（由 T19 与后端集成测试覆盖）。

## 8. 故障排查

| 现象 | 处理 |
| --- | --- |
| `net::ERR_CONNECTION_REFUSED` | webServer 未就绪或被 `E2E_NO_WEBSERVER=1` 关闭；检查 5173 是否监听 |
| `Executable doesn't exist ... chromium` | `npx playwright install chromium`，或临时 `E2E_BROWSER_CHANNEL=chrome` |
| 登录 401/500（真后端模式） | 检查后端迁移/种子数据与 MySQL；`curl http://127.0.0.1:8080/actuator/health` |
| 用例被 skip | 真后端模式下 `/api/v1/public/config` 不可达；或依赖预置数据的用例在真后端模式主动跳过（日志有原因） |
| 端口被占用 | 5173/8080 被占用时先 `pnpm dev:stop` 或改用 `E2E_BASE_URL` |
| 产物污染工作区 | 产物已被 `.gitignore` 忽略；清理：`rm -rf test-results playwright-report` |
