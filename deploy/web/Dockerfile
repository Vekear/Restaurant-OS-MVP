# =============================================================================
# 餐厅点餐系统 MVP — Web 前端构建/产物发布镜像（Vue 3 + Vite，纯 JavaScript）
#
# 构建上下文 = 仓库根目录（docker-compose.yml: build.context: .）
#   docker build -f deploy/web/Dockerfile -t restaurant-web .
#
# 设计说明（与 docker-compose 的 web 服务配合）：
#   - 构建阶段用 pnpm 产出 web/dist
#   - 发布阶段是一个「一次性」容器：把 dist 拷贝到 compose 命名卷 web_dist（挂载点 /out），
#     随后 web 服务以退出码 0 结束；网关 nginx 以 :ro 方式挂载同一个卷到
#     /usr/share/nginx/html 完成静态托管（见 deploy/nginx/nginx.conf）
#   - 这样网关仍是唯一的 80 端口入口，避免额外一层静态服务器
#   - 本机无 Docker，本镜像构建/运行状态：未验证
# =============================================================================

# ---------- 构建阶段 ----------
FROM node:24-alpine AS build

WORKDIR /workspace

RUN corepack enable && corepack prepare pnpm@12.10.1 --activate

# 一次性拷贝 web 源码（含 pnpm-lock.yaml，若 web 团队提供）
COPY web/ ./web/

RUN cd web \
    && pnpm install --no-frozen-lockfile \
    && pnpm build

# ---------- 发布阶段 ----------
FROM alpine:3.20

WORKDIR /app

COPY --from=build /workspace/web/dist ./dist

# 校验产物非空，避免空目录被发布进共享卷
RUN test -f /app/dist/index.html || (echo "web/dist/index.html 不存在，构建失败" >&2; exit 1)

CMD ["sh", "-c", "set -e; mkdir -p /out; echo '[web] 发布 web/dist → /out'; rm -rf /out/* /out/.[!.]* 2>/dev/null || true; cp -a /app/dist/. /out/; echo \"[web] 完成，共 $(find /out -type f | wc -l) 个文件\""]
