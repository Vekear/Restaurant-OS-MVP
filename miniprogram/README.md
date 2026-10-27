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
   │  ├─ menu/menu.vue        # 菜单：分类、搜索、图片兜底、加购
   │  ├─ cart/cart.vue        # 购物车：数量增减、删除、清空、合计
   │  ├─ mine/mine.vue        # 个人中心：登录态、服务入口、运行环境
   │  └─ login/login.vue      # 微信登录（uni.login → 后端换 token）
   ├─ components/
   │  ├─ AppImage.vue         # 图片组件（加载失败兜底本地占位图）
   │  ├─ QuantityStepper.vue  # 数量步进器（纯 CSS 图标）
   │  └─ EmptyState.vue       # 空态/错误态
   ├─ api/
   │  ├─ request.js           # 请求封装：token、401、统一错误、loading、超时
   │  ├─ auth.js              # 登录（真实/mock 双通道）
   │  └─ menu.js              # 分类与菜品（字段归一化）
   ├─ store/
   │  ├─ user.js              # 登录态（内存 + 本地存储）
   │  └─ cart.js              # 购物车（本地持久化 + tabBar 角标）
   ├─ mock/                   # 演示数据（6 分类 / 15 菜 / 假用户）
   ├─ utils/                  # config / storage / session / format / image
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
pnpm dev:mp-weixin        # 微信开发者工具导入 dist/dev/mp-weixin
```

### 构建

```bash
pnpm build:mp-weixin   # 产物：dist/build/mp-weixin
pnpm build:h5          # 产物：dist/build/h5
pnpm build:h5:mock     # 演示模式 H5 产物
```

微信开发者工具：导入 `dist/build/mp-weixin`，测试号可直接使用（`project.config.json` 中为 `touristappid`）。
本地联调时需在开发者工具中勾选「不校验合法域名」，因为后端地址为 `http://127.0.0.1:8080`。

## 4. 环境变量

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | API 根地址，可写 `https://host` 或 `https://host/api/v1`，自动补齐 `/api/v1` | 空 |
| `VITE_MOCK` | `true` 时使用内置演示数据（不请求后端） | `false`（`.env.mock` 为 `true`） |
| `VITE_PROXY_TARGET` | H5 开发代理目标（仅 `pnpm dev` 生效） | `http://127.0.0.1:8080` |

未配置 `VITE_API_BASE_URL` 时：H5 使用同源 `/api/v1`（走 Vite 代理），小程序使用 `http://127.0.0.1:8080/api/v1`（可用 `.env.development.local` 覆盖为局域网地址做真机调试）。

## 5. 接口契约（当前假设，集中封装便于调整）

> 后端接口文档（docs/02-api.md）在本阶段尚未定稿，以下按 brief 的统一响应约定实现，并与后端联调时只需修改 `src/api/`。

统一响应：`{ code, message, data, traceId }`，`code === 0` 视为成功；否则用 `message` 提示。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/wx/login` | 入参 `{ code }`（`uni.login` 获取），返回 `{ token, user }`（兼容 `accessToken`/`userInfo` 命名） |
| GET | `/menu/categories` | 菜品分类列表 |
| GET | `/menu/items?categoryId=&keyword=` | 菜品列表（支持分类过滤与关键字搜索） |

字段归一化：分类兼容 `id/name/sort`；菜品兼容 `price/image/imageUrl/coverUrl/stock/sales/soldOut/tags`，列表结果兼容 `list/records/items/content/rows/data`。

## 6. 关键实现说明

- **请求封装**（`src/api/request.js`）：自动附带 `Authorization: Bearer <token>`；HTTP 401 时清理登录态、广播 `auth:unauthorized` 并跳转登录页（带防重入）；错误统一为 `{ code, message, traceId, statusCode }`；网络异常提示「网络异常，请检查网络或后端服务是否已启动」；支持 `loading` 计数与自定义 `silent`。
- **登录**：小程序端 `uni.login({ provider: 'weixin' })` 取 `code` → `POST /auth/wx/login`；H5 无微信环境时生成降级 code。mock 模式下不发请求，直接返回假 token 与假用户（`wechat.mock=true` 场景）。
- **图片兜底**：`AppImage.vue` 统一处理相对路径与绝对地址；加载失败自动切换 `static/placeholder.png`；后端 `storage.type=local` 时 `/files/**` 会拼上源站地址。
- **购物车**：模块级 reactive + `uni.setStorageSync` 持久化（key `restaurant_mp_cart_v1`），单菜上限 99 份；数量变化同步 tabBar 角标，首次启动 tabBar 未挂载时自动重试（最多 3 次），避免首屏角标丢失。
- **登录态**：token 与用户信息存于本地存储（`restaurant_mp_token` / `restaurant_mp_user`），启动时恢复；退出登录清除缓存。
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

- 分类来自后端：`招牌推荐`、`热菜`；菜品来自后端：`后端招牌鸡 ¥66.00 已售 88`、`后端小炒肉 ¥36.00`（`stock=0` 渲染为「已售罄」）。
- 停掉后端时页面展示错误态：「菜单加载失败 / 网络异常，请检查网络或后端服务是否已启动 / 重新加载」，无 JS 异常。
- 说明：测试用的桩服务与静态服务器为临时脚本，未纳入代码仓库。

### 7.7 未验证项（缺少环境）

- **微信小程序真机/开发者工具运行**：本机未安装微信开发者工具，且无微信 AppID → 「未验证」。
- **真实微信登录换取 openid**：无 AppID/Secret，仅验证 mock 链路 → 真实链路「未验证」。
- **与后端联调**（`/auth/wx/login`、`/menu/*`）：后端尚未交付，接口按约定封装 → 「未验证」。
- **扫码入座、下单、模拟支付、状态轮询、图片上传**：属于 T17 范围，本阶段未实现。

## 8. 后续（T17 预告，不在本阶段范围）

`uni.scanCode` 解析桌台二维码 → `/dine-in/bind` 绑定会话 → 下单（`Idempotency-Key`）→ 模拟支付 → 订单状态页（轮询/WebSocket）→ 图片上传（local 走 `uni.uploadFile`，OSS 走 Post Policy 表单直传）。

## 9. 常见问题

| 现象 | 处理 |
| --- | --- |
| `ERR_PNPM_IGNORED_BUILDS` | 确认 `pnpm-workspace.yaml` 中 `allowBuilds` 已包含 `esbuild`、`core-js`、`core-js-pure`（pnpm 12 起不再读取 package.json 的 `pnpm` 字段） |
| 小程序请求失败 | 开发者工具「详情 → 本地设置」勾选「不校验合法域名」，并确认后端已启动在 8080 |
| H5 页面空白 | 使用 `pnpm dev:h5:mock` 先验证前端自身；对接后端时确认代理目标端口正确 |
| `project.config.json` 中 appid 为 touristappid | 未配置真实 AppID 时的测试号占位，正式发布前在 `src/manifest.json` 的 `mp-weixin.appid` 填写 |
