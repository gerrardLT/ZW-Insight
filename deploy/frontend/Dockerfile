# ============================================================
# Stage: Serve - 服务器端不再做 npm build（避免资源争抢与 SSH 断连）
# ============================================================
# 变更背景：2026-09-24 实测生产机上做 vite build（rendering chunks 5 分钟无输出）导致 SSH 断连、
#   负载飙升 146、整机不可服务约 40 分钟（详见 CI job frontend-build 注释）。
# 修复方案：runner 侧构建 dist 产物，服务器只做 COPY 到 nginx（单阶段镜像）。
# ------------------------------------------------------------
FROM nginx:1.25-alpine AS builder

# 复制构建产物到默认 nginx 目录（部署上下文是项目根 /root/zwi-deploy/，故路径为 zw-insight-web/dist）
COPY --chmod=755 zw-insight-web/dist ./usr/share/nginx/html/

# 复制自定义 nginx 配置（含 SPA 路由 + API 反向代理）
COPY --chmod=644 deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
