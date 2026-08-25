# =============================================================================
# cz-ai-agent-frontend 微信云托管镜像
# 多阶段构建：Node 打包 dist → Nginx 托管静态资源 + 可选 /api 反向代理
# 构建时用 --build-arg VITE_API_BASE_URL=... 注入后端公网地址（无则回退本地）
# =============================================================================

# ---------- 阶段一：构建 ----------
FROM node:20-alpine AS build
WORKDIR /app

# 仅拷贝依赖清单，充分利用 Docker 层缓存
COPY package*.json ./
RUN npm install --no-audit --no-fund

# 拷贝源码
COPY . .

# 注入后端地址：优先用构建参数 VITE_API_BASE_URL；未传则读取 .env 文件（如 .env.production）
# 注意：不能用 ENV 强行设默认值（会覆盖 .env 导致地址永远不生效）
ARG VITE_API_BASE_URL
RUN if [ -n "$VITE_API_BASE_URL" ]; then VITE_API_BASE_URL="$VITE_API_BASE_URL" npm run build; else npm run build; fi

# ---------- 阶段二：运行 ----------
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
