# 小馆点餐 · 微信小程序（UniApp Vue3 + JavaScript）

餐厅堂食扫码点餐场景的微信小程序端，属于「餐厅点餐系统 MVP」四层架构中的用户层。
本阶段（T16）交付：**工程骨架 + 登录 + 菜单 + 购物车 + 个人中心**，可独立以演示数据（mock）运行，也可对接 Spring Boot 后端。

> 硬性约束：**全项目禁止 TypeScript**（无 `.ts` 文件、无 `lang="ts"`、无 typescript 依赖），统一使用 JavaScript + Vue3 `<script setup>` 语法。

---

## 1. 技术栈

| 层次 | 选型 | 版本 |
| --- | --- | --- |
| 框架 | UniApp（Vue3 编译器） | `@dcloudio/*` `3.0.0-5030320261023001` |
| 视图 | Vue 3（`<script setup>`） | 3.4.21 |
| 构建 | Vite | 5.2.8 |
| 样式 | SCSS（sass） | 1.78.0 |
| 目标端 | 微信小程序 `mp-weixin`、H5 | — |

状态管理未引入 Pinia，使用轻量模块级 `reactive` store（`src/store/*`），减少小程序包体积与依赖。

## 2. 目录结构

```text
miniprogram/
├─ index.html                 # H5 入口（Vite）
├─ vite.config.js             # uni 插件 + 开发代理（/api、/ws、/files）
├─ package.json               # 依赖与构建脚本
├─ pnpm-workspace.yaml        # pnpm 12 构建脚本白名单（allowBuilds）
├─ jsconfig.json              # 编辑器 @ 别名提示（非 TypeScript 配置，仅 JavaScript）
├─ .env.development           # 默认开发环境（对接后端）
├─ .env.mock                  # 演示模式（VITE_MOCK=true）
├─ scripts/h5-smoke.js        # 可选：真实浏览器冒烟脚本（Chrome CDP）
└─ src/
   ├─ main.js                 # createSSRApp 入口
   ├─ App.vue                 # 全局生命周期：恢复登录态、同步购物车角标、401 广播
   ├─ pages.json              # 页面与 tabBar 配置
   ├─ manifest.json           # 应用信息（微信 AppID 留空）
   ├─ uni.scss                # 设计变量（品牌色/圆角/阴影/间距）
   ├─ styles/base.scss        # 全局基础样式
   ├─ pages/
   │  ├─ menu/menu.vue        # 菜单：分类、搜索、图片兜底、加购、桌台状态条
   │  ├─ cart/cart.vue        # 购物车：数量增减、删除、清空、去下单
   │  ├─ mine/mine.vue        # 个人中心：登录态、头像上传、订单入口、运行环境
   │  ├─ login/login.vue      # 微信登录（uni.login → 后端换 token）
   │  ├─ scan/scan.vue        # 扫码入座：uni.scanCode / 手输桌号 / 会话卡 / 结束用餐
   │  └─ order/
   │     ├─ confirm.vue       # 确认订单：桌台、菜品、备注、金额、提交
   │     ├─ pay.vue           # 模拟支付：展示真实订单金额，实际扣款 0.01
   │     ├─ status.vue        # 订单状态：步骤条 + 轮询 + 状态记录 + 取消
   │     └─ list.vue          # 我的订单：进行中 / 已完成 / 全部
   ├─ components/
   │  ├─ AppImage.vue         # 图片组件（加载失败兜底本地占位图）
   │  ├─ QuantityStepper.vue  # 数量步进器（纯 CSS 图标）
   │  └─ EmptyState.vue       # 空态/错误态
   ├─ api/
   │  ├─ request.js           # 请求封装：token、401、统一错误、X-Table-Session、Idempotency-Key
   │  ├─ auth.js              # 登录（真实/mock 双通道）
   │  ├─ menu.js              # 分类与菜品（字段归一化）
   │  ├─ dinein.js            # 扫码/绑定/会话 + 错误码友好文案
   │  ├─ order.js             # 堂食订单：创建/详情/列表/取消
   │  ├─ payment.js           # 创建支付单 / 模拟支付
   │  ├─ upload.js            # 文件策略 + local 上传 / OSS Post Policy 直传
   │  └─ profile.js           # 资料更新（头像）
   ├─ store/
   │  ├─ user.js              # 登录态（内存 + 本地存储）
   │  ├─ cart.js              # 购物车（本地持久化 + tabBar 角标）
   │  └─ table.js             # 桌台会话（restaurant_mp_table_session_v1）
   ├─ mock/                   # 演示数据（6 分类 / 15 菜 / 假用户 / 堂食会话与订单）
   ├─ utils/                  # config / storage / session / format / image / id / qr / tableSession
   └─ static/                 # logo、占位图、tabBar 图标、演示菜品图
```

## 3. 快速开始

```bash
cd miniprogram
pnpm install

# 演示模式（不依赖后端，菜单与登录均用内置假数据）
pnpm dev:h5:mock          # 浏览器打开 http://127.0.0.1:5174

# 对接后端开发
pnpm dev:h5               # H5 开发，/api 反向代理到 http://127.0.0.1:8080
VITE_PROXY_TARGET=http://127.0.0.1:8090 pnpm dev:h5   # 代理到本地契约桩（8090 专属测试端口）
pnpm dev:mp-weixin        # 微信开发者工具导入 dist/dev/mp-weixin
```

### 构建

```bash
pnpm build:mp-weixin        # 产物：dist/build/mp-weixin（对接真实后端）
pnpm build:mp-weixin:mock   # 演示模式小程序产物（内置假数据，无需后端）
pnpm build:h5               # 产物：dist/build/h5
pnpm build:h5:mock          # 演示模式 H5 产物
```

微信开发者工具：**先执行 `pnpm build:mp-weixin`**，再导入 `miniprogram/` 目录或直接导入 `dist/build/mp-weixin`。
仓库根的 `project.config.json` 已设置 `miniprogramRoot: "dist/build/mp-weixin"`，所以导入 `miniprogram/` 也能直接编译运行（未构建时会出现「在项目根目录未找到 app.json」）。
后端堂食/支付接口尚未交付时，建议先 `pnpm build:mp-weixin:mock` 生成演示模式产物，开发者工具里即可完整体验菜单、加购、扫码入座（mock）与模拟支付。
开发阶段热更新：运行 `pnpm dev:mp-weixin`，把 `miniprogramRoot` 改为 `dist/dev/mp-weixin`（或直接导入该目录）。
`project.config.json` 已关闭 `urlCheck`（不校验合法域名），因为后端地址为 `http://127.0.0.1:8080`；若被 `project.private.config.json` 覆盖，请确认其中 `setting.urlCheck` 也为 `false`。

## 4. 环境变量

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | API 根地址，可写 `https://host` 或 `https://host/api/v1`，自动补齐 `/api/v1` | 空 |
| `VITE_MOCK` | `true` 时使用内置演示数据（不请求后端） | `false`（`.env.mock` 为 `true`） |
| `VITE_PROXY_TARGET` | H5 开发代理目标（仅 `pnpm dev` 生效） | `http://127.0.0.1:8080` |

未配置 `VITE_API_BASE_URL` 时：H5 使用同源 `/api/v1`（走 Vite 代理），小程序使用 `http://127.0.0.1:8080/api/v1`（可用 `.env.development.local` 覆盖为局域网地址做真机调试）。

## 5. 接口契约（已对齐 `docs/02-api.md` 冻结契约）

> 端点路径以 `docs/02-api.md` §1 端点索引为准，统一封装在 `src/api/`；后端字段调整只需改这一层。

统一响应：`{ code, message, data, traceId }`，`code === 0` 视为成功；否则用 `message` 提示。分页响应 `data` 固定为 `PageResult`：`{ list, total, page, size, pages }`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/wechat/login` | 入参 `{ code }`（`uni.login` 获取），返回 `AuthTokenView`：`{ token, tokenType, expiresIn, user }`（`data` 层，`request.js` 已解包） |
| GET | `/menu/categories?merchantId=` | 分类列表 `CategoryView[]`（按 `sort ASC, id ASC`） |
| GET | `/menu/dishes?merchantId=&categoryId=&keyword=&sort=&page=&size=` | 菜品分页 `PageResult<DishSummaryView>`，`size` 默认 12（1–100），`sort` ∈ `default/price_asc/price_desc/sales_desc` |

堂食链路（T17，已接入同一契约）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/dine-in/scan` | 入参 `{ p, s }`，校验二维码签名，返回 `DineInSessionView` |
| POST | `/dine-in/bind` | 入参 `{ tableNo, s? }`（手输桌号兜底），返回 `DineInSessionView` |
| GET | `/dine-in/session` | 请求头 `X-Table-Session`，返回当前会话（无则 40401） |
| POST | `/dine-in/session/close` | 结束用餐（有进行中订单时 40916） |
| POST | `/dine-in/orders` | 下单：`X-Table-Session` + `Idempotency-Key`，body `{ items[{dishId,quantity}], remark, expectedPayAmount }` |
| GET | `/dine-in/orders?statusGroup=ACTIVE\|FINISHED\|ALL` | 我的订单分页 |
| GET | `/dine-in/orders/{id}` | 订单详情（状态页轮询此接口） |
| POST | `/dine-in/orders/{id}/cancel` | 取消订单（待支付/待接单可取消） |
| POST | `/payments` | 创建支付单 `{ orderId, orderType }`（`Idempotency-Key`），返回 `paidAmount = min(0.01, orderAmount)` |
| POST | `/payments/{payNo}/mock-pay` | 模拟支付（`Idempotency-Key`），返回 `PaymentView` |
| GET | `/files/policy?bizType=&ext=` | 文件策略：`local`（`uploadMode=backend`）或 `oss`（Post Policy） |
| POST | `/files/upload` | local 模式 `uni.uploadFile` 表单上传（`file`、`bizType`） |
| POST | `/files/confirm` | oss 直传后回执确认 |

字段归一化：分类 `id/name/sort/dishCount/status`；菜品 `id/name/categoryId/categoryName/price/originalPrice/imageUrl/stock/sales/status/soldOut/tags`（兼容 `image/coverUrl` 等历史命名）；`fetchDishes` 返回归一化后的 `PageResult`，页内渲染只需 `.list`。

## 6. 关键实现说明

- **请求封装**（`src/api/request.js`）：自动附带 `Authorization: Bearer <token>`；HTTP 401 时清理登录态、广播 `auth:unauthorized` 并跳转登录页（带防重入）；错误统一为 `{ code, message, traceId, statusCode }`；网络异常提示「网络异常，请检查网络或后端服务是否已启动」；支持 `loading` 计数与自定义 `silent`、自定义 `header`（供 T17 的 `X-Table-Session`、`Idempotency-Key` 使用）。
- **登录**：小程序端 `uni.login({ provider: 'weixin' })` 取 `code` → `POST /auth/wechat/login`；H5 无微信环境时使用一次性回退 code（需后端 `wechat.mock=true`）。mock 模式下不发请求，直接返回假 token 与假用户。
- **平台判定**：使用 uni-app 条件编译 `// #ifdef MP-WEIXIN` 得到 `IS_MINI_PROGRAM`，而非运行时探测 `wx`（H5 产物中的 `wx` 兼容对象不可靠，且 `uni.login` 在 H5 不可用）。
- **图片兜底**：`AppImage.vue` 统一处理相对路径与绝对地址；加载失败自动切换 `static/placeholder.png`；后端 `storage.type=local` 时 `/files/**` 会拼上源站地址。
- **购物车**：模块级 reactive + `uni.setStorageSync` 持久化（key `restaurant_mp_cart_v1`），单菜上限 99 份；数量变化同步 tabBar 角标，首次启动 tabBar 未挂载时自动重试（最多 3 次），避免首屏角标丢失。
- **登录态**：token 与用户信息存于本地存储（`restaurant_mp_token` / `restaurant_mp_user`），启动时恢复；退出登录清除缓存。
- **桌台会话**（`store/table.js` + `utils/tableSession.js`）：绑定成功后持久化 `DineInSessionView`；`request.js` 对后续请求自动附加 `X-Table-Session`；会话过期（`expireAt`）或 40911 时提示重新扫码。
- **扫码入座**（`pages/scan/scan.vue`）：`uni.scanCode` 解析小程序码/二维码中的 `p`、`s` 参数 → `POST /dine-in/scan`；无相机环境（H5）提供手输桌号 `POST /dine-in/bind` 兜底；错误码 40004/40910/40917 等映射为中文提示；已入座显示桌号、商家、到期时间与「结束用餐」。
- **下单与幂等**：确认页提交前生成一次 `Idempotency-Key`（`utils/id.js`）并在重试时复用；`expectedPayAmount` 传真实应付金额，后端金额变化返回 40005 时提示刷新；成功后清空购物车并跳转支付页。
- **模拟支付**：支付页展示 `orders.pay_amount` 真实金额，明确提示「实际扣款 ¥0.01（演示环境），报表按订单金额统计」；调用 `/payments/{payNo}/mock-pay` 后进入状态页。
- **状态页轮询**：默认每 5 秒轮询 `GET /dine-in/orders/{id}`（小程序 WebSocket 可用时由后续阶段接入，轮询作为兜底）；页面隐藏/卸载时停止定时器；支持下拉刷新、取消订单、查看状态流转记录。
- **图片上传**（`api/upload.js`）：读取 `/files/policy`；`storageType=local` 时用 `uni.uploadFile` 直传后端再回填 URL，`storageType=oss` 时按 Post Policy 表单直传 OSS 后调用 `/files/confirm`；`AppImage` 对失败图片兜底本地占位图。
- **无 TypeScript**：全部 `.js` + `<script setup>`，`jsconfig.json` 仅用于编辑器识别 `@/` 别名（`allowJs`、`checkJs: false`）。

## 7. 验证记录（真实执行）

执行环境：Windows + Node 24.19.0 + pnpm 12.10.1。

### 7.1 依赖安装

```text
$ pnpm install
Packages: +489
dependencies:
+ @dcloudio/uni-app 3.0.0-5030320261023001
+ @dcloudio/uni-components 3.0.0-5030320261023001
+ @dcloudio/uni-h5 3.0.0-5030320261023001
+ @dcloudio/uni-mp-weixin 3.0.0-5030320261023001
+ vue 3.4.21
devDependencies:
+ @dcloudio/types 3.4.31
+ @dcloudio/uni-cli-shared 3.0.0-5030320261023001
+ @dcloudio/uni-stacktracey 3.0.0-5030320261023001
+ @dcloudio/vite-plugin-uni 3.0.0-5030320261023001
+ sass 1.78.0
+ vite 5.2.8
Done in 2.4s using pnpm v12.10.1     # exit code 0
```

### 7.2 小程序构建

```text
$ pnpm build:mp-weixin
Compiler version: 5.33（vue3）
Compiling...
DONE  Build complete.
Run method: open Weixin Mini Program Devtools, import dist\build\mp-weixin run.   # exit code 0
```

产物 `dist/build/mp-weixin`：`app.js/app.json/app.wxss`、`pages/{menu,cart,mine,login}`、`components/`、`static/`（含 tabBar 6 个图标、6 张演示菜品图）、`project.config.json`。

### 7.3 H5 构建

```text
$ pnpm build:h5
DONE  Build complete.          # exit code 0，产物 dist/build/h5
```

### 7.4 无 TypeScript 检查

```text
$ find . -name "*.ts" -not -path "./node_modules/*" -not -path "./dist/*"   # 无输出
$ grep -rn 'lang="ts"' src                                                # 无匹配
$ grep -rn "typescript" package.json src                                  # 无匹配
```

### 7.5 浏览器端到端走查（H5 + mock 模式，Chrome Headless + CDP，视口 390×844）

| 场景 | 结果 |
| --- | --- |
| 菜单页渲染 | 15 张菜品卡、7 个分类标签（含「全部」）、15 个加购按钮，`bodyScrollWidth = 390` 无横向溢出 |
| 图片映射 | 菜品图正确加载 `/static/demo/dish-*.png` |
| 登录 | 点击「微信一键登录」→ 写入 `mock-token-*` 与用户信息 → 跳转菜单页 |
| 个人中心 | 显示「微信用户 / 已登录 · 堂食会员」、运行环境（演示数据 + 接口地址） |
| 购物车 | 预置 2 条数据：条目 2 条、合计 `108.00`、步进器 `2 / 1` |
| 数量增减 | 点「+」后合计 `156.00`、步进器 `3 / 1`、tabBar 角标 `4` |
| 控制台错误 | 0 条 |

> 上述走查已固化为可复现脚本 `scripts/h5-smoke.js`（Node ≥ 22 内置 WebSocket + Chrome DevTools Protocol）：

```text
$ node scripts/h5-smoke.js http://127.0.0.1:5174
SMOKE_PASS: 菜单卡片 15 张 | 分类标签 7 个（含全部） | 无横向溢出 | 购物车角标为 3 |
登录页初始无 token | 点击登录成功 | 登录后获得 mock token | 购物车 2 条、合计 108.00 |
加购后合计 156.00、角标 4 | 无控制台错误     # exit code 0
```

### 7.6 真实 HTTP 链路验证（生产构建 + 网关反代 + 桩后端）

以静态服务器托管 `dist/build/h5` 并反代 `/api` 到 8080 的临时桩服务（统一响应 `{code,message,data,traceId}`），验证非 mock 链路：

- 分类来自后端：`招牌推荐`、`热菜`；菜品来自后端：`后端招牌鸡 ¥66.00 已售 88`、`后端小炒肉 ¥36.00`（`stock=0` 渲染为「已售罄」）；实际请求为 `GET /api/v1/menu/dishes?page=1&size=50`（无 404，路径与冻结契约一致）。
- 登录链路（H5 真实 HTTP）：点击「微信一键登录」→ `POST /api/v1/auth/wechat/login`（body `{ code: "h5_..." }`）→ 保存 `real-token-from-stub` 与用户 `后端联调用户` → 跳转菜单并继续请求分类/菜品；控制台 0 错误。
- 停掉后端时页面展示错误态：「菜单加载失败 / 网络异常，请检查网络或后端服务是否已启动 / 重新加载」，无 JS 异常。
- 说明：测试用的桩服务与静态服务器为临时脚本，未纳入代码仓库。

### 7.7 未验证项（缺少环境）

- **微信小程序真机/开发者工具运行**：本机未安装微信开发者工具，且无微信 AppID → 「未验证」。
- **真实微信登录换取 openid**：无 AppID/Secret，仅验证 mock 链路 → 真实链路「未验证」。
- **与真实后端联调**（`/menu/*`、`/dine-in/*`、`/payments/*`）：后端堂食接口尚在 T08/T09 排队，本阶段用契约桩验证 → 「未验证」。
- **微信/支付宝真实支付**：仅实现 `mock-pay` → 真实支付「未验证」。
- **OSS 直传**：本机无阿里云 OSS 凭证，仅验证 local 上传分支 → OSS 分支「未验证」。

### 7.8 T17 堂食链路验证（H5 + 契约桩，端口 8090）

> 8080 固定留给真实 Spring Boot 后端；本次验证的临时桩服务监听 8090，脚本未纳入仓库，验证结束后已关闭（`netstat -ano | findstr :8090` 无 LISTENING）。

启动方式：

```bash
VITE_PROXY_TARGET=http://127.0.0.1:8090 pnpm dev:h5   # 代理到本地契约桩
# 另一个终端：node <临时目录>/t17/stub.cjs           # 契约桩，监听 8090
```

浏览器端到端（Chrome Headless 9335 + CDP，视口 390×844，`Network.setCacheDisabled`）：

| 步骤 | 断言结果 |
| --- | --- |
| 登录 | `POST /api/v1/auth/wechat/login` → 写入 `real-token-from-stub`，跳转菜单 |
| 扫码/手输入座 | 手输 `A01` → `POST /api/v1/dine-in/bind` → 会话持久化；菜单顶部显示「A01 · 桩服务桌台」 |
| 加购 | 菜品卡片「+」→ 购物车 2 件、合计 `120.00`、tabBar 角标同步 |
| 确认订单 | 桌台条、菜品明细、备注、金额 `120.00`；提交前 `expectedPayAmount` 一致 |
| 下单 | `POST /api/v1/dine-in/orders`，请求头含 `X-Table-Session` 与 `Idempotency-Key: dinein-order-*` |
| 支付页 | 展示真实金额 `120.00`，提示「实际扣款 ¥0.01（演示环境），报表按订单金额统计」；倒计时 14:58 |
| 模拟支付 | `POST /api/v1/payments` + `POST /api/v1/payments/P8801/mock-pay`，均带 `Idempotency-Key` |
| 状态轮询 | 依次观察到 `已接单 → 制作中 → 已出餐 → 已完成`（每 5 秒轮询），状态记录 6 条 |
| 我的订单 | 「进行中」0 条、「已完成」1 条（`已完成`） |
| 桌台冲突 | 手输 `A99` → 弹窗「桌台已被占用，请联系服务员」（40910） |
| 图片上传 | 个人中心头像 → `GET /files/policy` → `POST /files/upload`（`uni.uploadFile`）→ `PUT /auth/profile`，toast「头像已更新」 |
| 控制台错误 | 0 条 |

截图（临时目录，未入库）：菜单入座态、确认订单、支付页、已完成状态页、订单列表、扫码空态/冲突弹窗。

构建：`pnpm build:mp-weixin`、`pnpm build:h5` 均 `DONE Build complete`（见 7.2/7.3）。

## 8. 后续（T18+ 预告，不在本阶段范围）

- 小程序端 WebSocket 订阅订单状态（轮询保留为兜底）。
- 优惠券选择、订单评价、发票等营销能力。
- 真机扫码与微信开发者工具联调（需真实 AppID 与后端堂食接口）。

## 9. 常见问题

| 现象 | 处理 |
| --- | --- |
| `ERR_PNPM_IGNORED_BUILDS` | 确认 `pnpm-workspace.yaml` 中 `allowBuilds` 已包含 `esbuild`、`core-js`、`core-js-pure`（pnpm 12 起不再读取 package.json 的 `pnpm` 字段） |
| 小程序请求失败 | 开发者工具「详情 → 本地设置」勾选「不校验合法域名」，并确认后端已启动在 8080 |
| H5 页面空白 | 使用 `pnpm dev:h5:mock` 先验证前端自身；对接后端时确认代理目标端口正确 |
| `project.config.json` 中 appid 为 touristappid | 未配置真实 AppID 时的测试号占位，正式发布前在 `src/manifest.json` 的 `mp-weixin.appid` 填写 |
| 模拟器启动失败：`Error: app.json: 在项目根目录未找到 app.json` | 开发者工具导入的是源码根目录，而 `app.json` 在构建产物里。先 `pnpm build:mp-weixin`，项目已配置 `miniprogramRoot: "dist/build/mp-weixin"`；若仍报错，重新打开项目或直接导入 `dist/build/mp-weixin` |
| 小程序里请求 http://127.0.0.1:8080 被拦截 | 开发者工具「详情 → 本地设置」勾选「不校验合法域名」（仓库已把 `urlCheck` 设为 `false`，如仍生效请检查 `project.private.config.json`） |
