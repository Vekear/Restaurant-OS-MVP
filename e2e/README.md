# E2E（Playwright）脚手架

餐厅点餐系统 MVP 的浏览器端到端测试目录。**当前为 T19a 交付的脚手架**：配置、运行脚本与目录约定已就绪；业务链路用例由 **T19** 补充。

> 全目录使用 **JavaScript**（`.js` / `.mjs`），严禁 TypeScript；Playwright 配置使用 ESM（`"type": "module"`）。

## 1. 前置依赖（需先在本机启动）

E2E 刻意**不通过 `webServer` 自动拉起服务**，复用开发环境，避免与 `scripts/dev-local.ps1` 争抢端口：

| 依赖 | 地址 | 说明 |
| --- | --- | --- |
| Web 前端（Vite dev） | <http://127.0.0.1:5173> | 在 `web/` 执行 `pnpm dev`（或 `npm run dev`） |
| 后端 API | <http://127.0.0.1:8080> | 健康检查：`GET /actuator/health` 返回 `{"status":"UP"}` |
| MySQL / Redis | 127.0.0.1:3306 / 6379 | 种子数据与登录依赖，见 `docs/06-deployment.md` |

一键启动本机开发环境（MySQL/Redis 已就绪时）：

```powershell
pnpm dev            # = powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-local.ps1
```

演示账号（种子数据，见 brief §9）：

| 账号 | 密码 | 角色 | 主要入口 |
| --- | --- | --- | --- |
| `admin` | `admin123` | ADMIN | `/admin/dashboard` |
| `merchant` | `merchant123` | MERCHANT | `/merchant/dashboard` |
| `customer` | `customer123` | CUSTOMER | `/menu`、`/orders` |

## 2. 安装

```bash
cd e2e
npm install            # 或 pnpm install
npx playwright install chromium   # 首次运行需下载浏览器（本机已缓存可跳过）
```

## 3. 运行

```bash
cd e2e
npm test                            # 无头模式，默认 http://127.0.0.1:5173
npm run test:headed                 # 有界面
npm run test:ui                     # Playwright UI（调试/录屏）
E2E_BASE_URL=http://127.0.0.1 npm test   # 走 Nginx 网关（需先 pnpm dev:nginx）
```

- 报告与产物：`e2e/playwright-report/`（HTML）、`e2e/test-results/`（trace / 失败截图 / 视频）。
- 根 `.gitignore` 已覆盖 `e2e/test-results/`、`e2e/playwright-report/`、`e2e/blob-report/`、`e2e/.cache/`；`e2e/.gitignore` 作为补充。
- CI 下自动 `retries=1`、`workers=1`、`forbidOnly`；本地默认串行（`fullyParallel: false`），避免下单/接单等写操作互相污染。

## 4. 目录约定（T19 在此扩展）

```text
e2e/
├── playwright.config.js   # baseURL / 超时 / 报告 / chromium project
├── tests/
│   └── placeholder.spec.js  # 当前占位（test.skip），T19 替换为真实用例
├── package.json
└── README.md
```

编写用例时的约定（T19 落地）：

1. 文件名 `tests/<主题>.spec.js`；可复用步骤放 `tests/helpers/`（T19 新增）。
2. 断言以用户可见文案与路由为主，接口细节（RBAC 403、幂等、验签）由后端集成测试覆盖，避免重复。
3. 数据隔离：使用演示种子数据；写操作（下单/支付/接单/取消）需在用例内自清理，不依赖执行顺序。
4. 不得引入 TypeScript；不提交 `test-results/`、`playwright-report/`、trace、截图等产物。
5. 依赖真实服务，因此本目录用例在未启动 web/backend 时不应被当作「通过」——T19 可在 CI 前置 smoke 检查。

## 5. 当前状态与未验证项

- 已交付：Playwright 配置、`npm` 脚本、占位用例、目录与产物约定、`.gitignore` 双保险。
- **未验证**：真实业务链路（登录/RBAC、下单支付、接单出餐、报表导出等）尚无 E2E 用例，均由 **T19** 补充并执行；本目录的浏览器安装与真实运行需在 T19 阶段复验。
- 本机演示环境使用模拟支付与 `storage.type=local`（无 OSS/微信凭证），涉及真实第三方回调的 E2E 不在本 MVP 范围内（见 brief §4.6）。

## 6. 故障排查

| 现象 | 处理 |
| --- | --- |
| `net::ERR_CONNECTION_REFUSED` | 未启动 web（5173）或 backend（8080）；先 `pnpm dev` 或手动启动 |
| 登录 401/500 | 检查后端是否完成迁移与种子数据、MySQL 是否可用；`curl http://127.0.0.1:8080/actuator/health` |
| 浏览器缺失 | `npx playwright install chromium` |
| 端口被占用 | 5173/8080 被其他进程占用时，先 `pnpm dev:stop` 或改用 `E2E_BASE_URL` |
| 产物污染工作区 | 产物已被 `.gitignore` 忽略；如需清理：`rm -rf test-results playwright-report` |
