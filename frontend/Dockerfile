FROM crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/lxf_ai/node:20-alpine AS build
WORKDIR /app

# 2C4G 云主机：Node 堆不宜过大（需留给 esbuild/Rollup 子进程），建议宿主机加 2G swap
ARG LOW_MEM_BUILD=1
ARG NODE_HEAP_MB=1280
# 国内 ECS 默认 npmmirror；海外或自建代理可覆盖
ARG NPM_REGISTRY=https://registry.npmmirror.com

# 构建阶段必须安装 devDependencies（vite 在 devDependencies），不可设 NODE_ENV=production
ENV CI=true
ENV NODE_OPTIONS=--max-old-space-size=${NODE_HEAP_MB}
ENV VITE_LOW_MEM_BUILD=${LOW_MEM_BUILD}
ENV VITE_BUILD_COMPRESS=
ENV UV_THREADPOOL_SIZE=2

COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
# 网络不稳定时 ECONNRESET 常见：镜像源 + 重试，勿用 --prefer-offline（无缓存反而失败）
RUN npm config set registry "${NPM_REGISTRY}" \
  && npm config set fetch-retries 5 \
  && npm config set fetch-retry-mintimeout 20000 \
  && npm config set fetch-retry-maxtimeout 120000 \
  && set -e; \
     for attempt in 1 2 3 4 5; do \
       echo ">>> npm ci attempt ${attempt}/5 (registry=${NPM_REGISTRY})"; \
       npm ci --no-audit --no-fund --include=dev && break; \
       if [ "$attempt" = "5" ]; then exit 1; fi; \
       sleep $((attempt * 15)); \
     done

COPY frontend/ .
RUN npm run build:prod

FROM crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/lxf_ai/nginx:1.27-alpine
ENV BACKEND_HOST=backend \
    BLOG_EXTERNAL_API_TRUSTED_PROXY_IP=127.0.0.1 \
    BLOG_EXTERNAL_API_FORWARDED_PROTO=http
COPY frontend/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
