# ============================================================
# Stage 1: Build - 基于 node:20-alpine 构建前端产物
# ============================================================
FROM node:20-alpine AS builder

WORKDIR /app

# 使用国内镜像源加速依赖安装（先复制包描述文件，利用 Docker 缓存层）
COPY zw-insight-web/package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com

# 复制前端源码并执行构建（vue-tsc 类型检查 + vite build）
COPY zw-insight-web/ .
RUN npm run build

# ============================================================
# Stage 2: Serve - 使用 nginx:1.25-alpine 提供静态资源服务
# ============================================================
FROM nginx:1.25-alpine

# 复制构建产物到 nginx 默认目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制自定义 nginx 配置（含 SPA 路由 + API 反向代理）
COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
