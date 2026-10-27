# 拾味点餐 · 餐厅点餐系统 MVP

一个**可运行的餐厅点餐 MVP**：外卖 Web（顾客 / 商家 / 管理后台）+ 堂食微信小程序（UniApp）+ Spring Boot 3 后端 + MySQL / Redis / Nginx 分层架构。支持双订单状态机、模拟支付、库存防超卖、幂等下单、Spring Task 超时取消、WebSocket 实时推送、OSS 直传与本地存储降级、Swagger、POI Excel 导出与 ECharts 报表。

> 说明：本 README 为部署与运行骨架（T04 产出）。业务模块按团队任务 DAG 逐步落地；命令与端口已按 `docs/reference/brief.md` / `docs/reference/plan.md` 固化。

## 技术栈（严格四层）

| 层 | 技术 |
|---|---|
| 用户层 | Vue 3 + **JavaScript（禁用 TypeScript）** + Vite + Pinia + Vue Router + Element Plus + ECharts；微信小程序 + UniApp（Vue3 语法，JavaScript） |
| 网关层 | Nginx：静态托管、`/api` 反代、`/files`、`/ws` Upgrade、`/swagger-ui`、`/v3/api-docs`、SPA fallback、gzip、请求体上限 10m |
| 应用层 | Spring Boot 3 + Java 17 + Spring MVC、Spring Security + JWT、Spring Task、Spring Cache + Redis、SpringDoc、Apache POI、Spring WebSocket（STOMP） |
| 数据层 | MySQL 8 + Flyway、MyBatis-Plus、Redis、本地磁盘兜底存储 |

## 目录结构

```text
File2/
├─ backend/                 # Spring Boot 3 后端（Maven 单模块，release 17）
├─ web/                     # Vue 3 外卖 Web（顾客 / 商家 / 管理后台，Vue3 + JavaScript）
├─ miniprogram/             # 堂食微信小程序（UniApp Vue3 + JavaScript）
├─ e2e/                     # Playwright E2E（qa 维护）
├─ deploy/
│  ├─ nginx/nginx.conf      # Docker 网关配置（backend:8080，root /usr/share/nginx/html）
│  ├─ nginx/nginx.local.conf# 本机 Nginx 配置（127.0.0.1:8080，root=web/dist）
│  ├─ backend/Dockerfile    # 后端镜像（maven 构建 → temurin JRE）
│  └─ web/Dockerfile        # Web 构建 + 产物发布到命名卷（一次性容器）
├─ scripts/
│  ├─ dev-local.ps1         # 本机一键开发编排（后端 + Web + 可选 Nginx）
│  ├─ smoke-test.ps1        # REST 冒烟（健康 / OpenAPI / 登录 / 菜单）
│  ├─ export-openapi.ps1    # 导出 docs/openapi.json
│  └─ check-compose.mjs     # docker-compose.yml 静态校验（无 Docker 可用）
├─ docs/
│  ├─ reference/            # brief.md / plan.md（唯一事实来源）
│  ├─ 00-requirements.md ~ 07-acceptance.md
│  └─ openapi.json          # 由 export-openapi.ps1 生成
├─ docker-compose.yml       # 五服务编排：mysql / redis / backend / web / nginx
├─ .env.example             # 环境变量样例（.env 不提交）
└─ package.json             # 根脚本入口（npm run dev / smoke / compose:check …）
```

## 快速开始

### 0. 前置环境

- Node.js ≥ 20.19、pnpm ≥ 9（本机实测 Node 24.19 / pnpm 12.10.1）
- JDK ≥ 17（本机 JDK 21，Maven 以 `release 17` 编译）、Maven 3.9+
- MySQL 8（`127.0.0.1:3306`，库 `restaurant_mvp` / `restaurant_mvp_test`）
- Redis 7+（`127.0.0.1:6379`）
- 本机 Nginx（可选，Windows 二进制；见 `docs/06-deployment.md`）
- Docker + Docker Compose（可选；**本机当前未安装，Compose 运行态「未验证」**）

### 1. 本机开发（无 Docker，推荐当前环境）

```powershell
# 1) 准备环境变量（.env 已被 .gitignore 忽略，禁止提交）
Copy-Item .env.example .env

# 2) 确认 MySQL / Redis 已启动（由 Lead 统一维护）
#    MySQL 127.0.0.1:3306 root/123456；Redis 127.0.0.1:6379

# 3) 安装依赖（首次）
cd backend; mvn -q -DskipTests dependency:go-offline; cd ..
cd web; pnpm install; cd ..

# 4) 一键启动后端（8080）+ Web dev（5173）
npm run dev

# 5) 可选：构建 Web 产物并启动本机 Nginx（80）
cd web; pnpm build; cd ..
npm run dev:nginx

# 6) 停止本脚本启动的进程
npm run dev:stop
```

启动后：

- Web dev：<http://127.0.0.1:5173/>
- 后端 API：<http://127.0.0.1:8080/api/v1>
- 健康检查：<http://127.0.0.1:8080/actuator/health>
- Swagger UI：<http://127.0.0.1:8080/swagger-ui/index.html>
- Nginx（启用后）：<http://127.0.0.1/>

### 2. Docker Compose（本机无 Docker，运行态「未验证」）

```powershell
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
docker compose logs -f --tail=200
docker compose down          # 保留数据卷；彻底清理加 -v
```

- Web：<http://127.0.0.1/>；Swagger：<http://127.0.0.1/swagger-ui/index.html>
- 宿主机端口默认避让本机服务：MySQL `13306`、Redis `16379`、后端 `8080`、Nginx `80`（可在 `.env` 覆盖）
- 后端容器内数据库/Redis 主机名固定为 `mysql` / `redis`；Flyway 在应用启动时自动迁移
- 镜像与卷、healthcheck、依赖顺序见 `docs/06-deployment.md`

> ⚠️ 本机未安装 Docker：以上 Compose 命令仅经 `scripts/check-compose.mjs` 静态校验，**`docker compose up` 运行态未验证**，不得作为已通过证据。

## 环境变量

完整清单与双场景差异见 [`.env.example`](.env.example) 与 [docs/06-deployment.md](docs/06-deployment.md)。

| 分组 | 关键变量 |
|---|---|
| 通用 | `TZ`、`SPRING_PROFILES_ACTIVE`、`LOG_LEVEL` |
| MySQL | `MYSQL_ROOT_PASSWORD`、`MYSQL_DATABASE`、`MYSQL_HOST_PORT`、`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE`、`REDIS_HOST_PORT` |
| 鉴权 | `JWT_SECRET`（≥32 字节）、`JWT_EXPIRES_IN_SECONDS`、`TABLE_QR_SECRET`、`TABLE_QR_BASE_URL` |
| 存储 | `STORAGE_TYPE=local\|oss`、`STORAGE_LOCAL_DIR`、`STORAGE_LOCAL_URL_PREFIX`、`STORAGE_MAX_SIZE_MB`、`OSS_ENDPOINT`、`OSS_BUCKET`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`OSS_DIR`、`OSS_SIGN_EXPIRE_SECONDS` |
| 微信 | `WECHAT_MOCK`、`WECHAT_APP_ID`、`WECHAT_APP_SECRET` |
| 支付/超时 | `PAYMENT_MOCK_CHARGE_AMOUNT=0.01`、`PAYMENT_CALLBACK_BASE_URL`、`ORDER_PAYMENT_TIMEOUT_MINUTES`、`TABLE_SESSION_IDLE_MINUTES` |
| 跨域/JVM | `CORS_ALLOWED_ORIGINS`、`JAVA_OPTS`、`BACKEND_HOST_PORT`、`WEB_HOST_PORT` |

## 验证命令

在项目根目录执行（Windows PowerShell 5.1）：

```powershell
# Compose 静态校验（无 Docker 时自动 SKIP 运行态，并提示「未验证」）
node scripts/check-compose.mjs
npm run compose:check

# Nginx 双配置校验（本机 nginx，路径见 docs/06-deployment.md）
#   先建 .runtime/nginx 临时目录，详见文档
D:\Code_Environment\Nginx\nginx-1.30.6\nginx.exe -t -p .runtime/nginx -c <仓库绝对路径>/deploy/nginx/nginx.local.conf

# REST 冒烟（需后端已启动、Flyway 已迁移）
npm run smoke

# 网关冒烟（需 web/dist 与 Nginx 已启动）
npm run smoke:gateway

# 导出 OpenAPI（覆盖 docs/openapi.json）
npm run openapi:export

# 后端 / Web 构建（各自目录）
cd backend; mvn -q -DskipTests package; cd ..
cd web; pnpm build; pnpm test; cd ..

# 禁用项检查（qa 脚本落地后）
node scripts/check-no-ts.mjs
node scripts/check-no-secrets.mjs
```

## 已知「未验证」项（截至 T04 骨架）

- Docker / Docker Compose 运行态（本机无 Docker，仅静态校验 docker-compose.yml）
- Docker 镜像构建与容器 healthcheck（Dockerfile 仅静态审查）
- 真实阿里云 OSS 直传（无 AK/Bucket；默认 `storage.type=local` 降级）
- 真实微信 code2session / 真机扫码直开小程序（无 AppID/Secret/开发者工具；`WECHAT_MOCK=true`）
- 本机 Nginx 全链路（需 web/dist 构建产物与后端就绪后执行 `sudo` 无关的 80 端口启动）
- 业务链路（下单 / 模拟支付 / 状态机 / WebSocket / 导出 / 报表）随各模块落地后由 qa 验收

## 文档索引

- [需求](docs/00-requirements.md) · [架构](docs/01-architecture.md) · [API](docs/02-api.md) · [数据模型](docs/03-data-model.md)
- [部署](docs/06-deployment.md) · 测试（docs/04-testing.md，待落地） · 安全（docs/05-security.md，待落地） · 验收（docs/07-acceptance.md，待落地）
- [ADR](docs/adr/) · [OpenAPI](docs/openapi.json)（导出后生成）
- 项目唯一事实来源：[docs/reference/brief.md](docs/reference/brief.md)、[docs/reference/plan.md](docs/reference/plan.md)

## 协作约定（团队）

- **严禁 TypeScript**：不得出现 `.ts` / `tsconfig` / `lang="ts"` / `typescript` 依赖
- 不提交 `.env`、`node_modules`、`target`、`dist`、本地存储数据、运行日志
- 未实际运行的验证必须标注「未验证」；交接消息按 8 段模板（Agent / 任务 / 输入 / 产出 / 假设 / 风险 / 验收 / 下一步）

---

当前阶段：部署骨架（T04）。详细启动、排障、端口与卷说明见 [docs/06-deployment.md](docs/06-deployment.md)。
