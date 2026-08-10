# NovaMall

NovaMall 管理平台：完整系统管理、博客与 AI、微信公众号运营、电商商品/交易/支付，以及 History 历史学习平台。

## 架构

前后端分离，**UI 只在仓库根目录 `frontend/`，`backend/` 为纯 Java 后端**。

- **backend**：Maven 多模块 + `ruoyi-blog` + `ruoyi-wechat` + `ruoyi-mall-*`（商品/交易/支付）+ `ruoyi-history`
  - `ruoyi-admin`（启动入口）、RuoYi 基础模块，以及博客、微信、商城和历史学习业务模块
- **frontend**：Vue3 + Element Plus，含管理后台、公开博客 `/blog`、公开商城 `/mall`、公开历史学习站 `/history`
- **数据**：MySQL `nova_mall` + Redis（Token）

## 本地开发

### 1. 初始化数据库

新建本地数据库推荐使用 Docker 自动初始化：

```bash
cp .env.example .env
# 至少修改 MYSQL_ROOT_PASSWORD 和 TOKEN_SECRET
docker compose --env-file .env up mysql redis -d
```

Compose 的初始化清单已包含当前 Product V2、Payment、History 与 HN 所需脚本。MySQL 只会在空数据卷首次启动时执行初始化；存量库升级不会由部署流程自动执行，必须先备份并按照 [数据库初始化与升级说明](docs/database-upgrade.md) 手工处理。

> 后台商城菜单路由为 `/mall-admin/**`（如 `/mall-admin/spu`）；C 端商城为 `/mall`（如 `/mall`、`/mall/list`）。二者不可共用 `mall` 前缀，否则刷新后台页会 404。

非 Docker 本机启动时，修改 `backend/ruoyi-admin/src/main/resources/application-druid.yml` 中的数据库账号，库名建议 `nova_mall`。

### 2. 启动 Redis

Token 依赖 Redis，本地需运行 Redis（默认 `localhost:6379`）。

### 3. 启动后端

```bash
cd backend
# Linux/macOS：export TOKEN_SECRET="$(openssl rand -hex 64)"
# PowerShell：$env:TOKEN_SECRET = '<本地随机密钥>'
mvn spring-boot:run -pl ruoyi-admin -am
```

首次初始化账号为 `admin` / `admin123`，仅用于本地启动；首次登录后应立即修改密码。

### 4. 启动前端

```bash
cd frontend
npm ci
npm run dev
```

## Docker 构建（阿里云个人镜像仓库）

**构建上下文必须是仓库根目录 `NovaMall/`**，不能是 `frontend/` 子目录。

```bash
# 1. 登录私有仓库（否则会 unauthorized）
docker login crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com

# 2. 在仓库根目录构建
cd NovaMall
docker build -f frontend/Dockerfile -t crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/nova_mall/nova-mall-frontend:latest .
docker build -f backend/Dockerfile -t crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/nova_mall/nova-mall-backend:latest .

# 3. 推送（构建成功后再 push）
docker push crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/nova_mall/nova-mall-frontend:latest
docker push crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/nova_mall/nova-mall-backend:latest
```

常见错误：

| 现象 | 原因 | 处理 |
|------|------|------|
| `npm error signal SIGKILL`（多在 `rendering chunks`） | 构建峰值内存超过宿主机可用内存（2C4G 常见） | 见下方 **「2C4G 前端构建」**；或加大内存 / 加 swap / 在 CI 机构建镜像 |

### 2C4G 云主机前端构建（内存优化）

默认 `frontend/Dockerfile` 已启用低内存模式（`VITE_LOW_MEM_BUILD=1`、堆上限约 1280MB、关闭构建期 gzip/SVGO、拆分 echarts/mermaid 等大 chunk）。

**推荐（按优先级）：**

1. **增加 2GB swap**（最有效，几乎不花钱）：

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

2. **仅构建前端镜像**（避免与 MySQL 等同时抢内存）：

```bash
docker build -f frontend/Dockerfile -t nova-mall-web:local .
```

3. **调构建参数**（内存仍不足时）：

```bash
# 堆再降到 1GB
docker build -f frontend/Dockerfile --build-arg NODE_HEAP_MB=1024 -t nova-mall-web:local .
# 8G+ 机器可关闭低内存模式、略提速
docker build -f frontend/Dockerfile --build-arg LOW_MEM_BUILD=0 --build-arg NODE_HEAP_MB=2048 -t nova-mall-web:local .
```

4. **本地/CI 构建 dist 再 COPY**（小机器最稳）：在内存充足的机器 `cd frontend && npm ci && npm run build:prod:lowmem`，仅把 `dist/` 打进 nginx 镜像。

5. **`npm ci` 报 `ECONNRESET` / network aborted**（国内 ECS 常见）：Dockerfile 已默认 `registry.npmmirror.com` 并自动重试 5 次。仍失败可指定镜像：

```bash
docker build -f frontend/Dockerfile \
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com \
  -t nova-mall-web:local .
# 海外机器改用官方源
# --build-arg NPM_REGISTRY=https://registry.npmjs.org
```

| 优化项 | 作用 |
|--------|------|
| `reportCompressedSize: false` | 跳过构建期 gzip 体积统计，降低 rendering chunks 内存 |
| `manualChunks` 拆分 echarts/mermaid | 降低单 chunk 峰值 |
| `maxParallelFileOps: 2` | 适配 2 核，减少并行占用 |
| 关闭 `vite-plugin-compression` | gzip 由 nginx 负责 |
| `.dockerignore` | 缩小构建上下文，加快 COPY |
| `unauthorized: authentication required` | 未登录阿里云镜像仓库 | 先执行 `docker login crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com` |
| `tag does not exist` | 上一步 build 失败，本地没有镜像 | 先让 `docker build` 成功再 `docker push` |
| `COPY nginx.conf` not found | 构建上下文或路径错误 | 在仓库根目录构建，使用 `COPY frontend/nginx.conf` |

## Docker 一键部署

```bash
cp .env.example .env
# 修改数据库密码；使用 `openssl rand -hex 64` 生成 TOKEN_SECRET
docker compose --env-file .env up -d --build
```

AI Provider 统一在后台「AI博客 → AI模型配置」中维护，不再读取 `DEEPSEEK_API_KEY` 回退变量。本地 Docker 默认只开启 Mock 支付，Swagger/Druid 需在 `.env` 显式开启；`docker-compose.prod.yml` 固定关闭三者，且生产启动必须显式提供 `TOKEN_SECRET`。

ECS 生产发布由 `.github/workflows/deploy-ecs.yml` 负责，使用 commit SHA 镜像。GitHub Environment `production` 至少需要配置：

- Secret：`TOKEN_SECRET`、ACR、ECS SSH、MySQL、Redis 凭据；
- Variable：MySQL/Redis 地址与端口、服务端口、日志路径（推荐 `/opt/nova-mall/logs`）；
- OSS 关闭时无需填写 OSS Key；启用时再配置 Key、Bucket、Endpoint/Domain。

生产发布只更新应用容器，不会自动执行 SQL。升级代码前先完成 [存量数据库手工升级](docs/database-upgrade.md#存量数据库手工升级)。

### 云部署报错 `ExitCode expect in [0] but is 1`

多为远程执行命令失败（阿里云 ECS 云助手、流水线等）。按顺序排查：

| 步骤 | 命令 | 说明 |
|------|------|------|
| 1 | `docker compose build backend 2>&1 \| tail -50` | Maven 构建失败：检查网络、是否上传完整 `backend/` |
| 2 | `docker compose build frontend 2>&1 \| tail -50` | 前端构建失败：需存在 `frontend/package-lock.json` |
| 3 | `docker compose logs mysql` | 初始化 SQL 失败：仅对可丢弃的本地空库可重建数据卷；生产库必须从备份修复，禁止直接 `down -v` |
| 4 | `docker compose logs backend` | 连不上 MySQL/Redis：确认 mysql、redis 已 healthy |
| 5 | `docker compose ps` | backend 未 healthy：首次启动约 1–2 分钟，已放宽 `start_period` |

本地仅编译（不启 Docker）：

```bash
cd backend && mvn -B -DskipTests package -pl ruoyi-admin -am
cd frontend && npm ci && npm run build:prod
```

| 地址 | 说明 |
|------|------|
| http://localhost | 前端（Nginx 反代 `/prod-api` → 后端） |
| http://localhost:8080 | 后端 API |
| http://localhost/blog | 博客公开前台 |
| http://localhost/mall | 商城公开前台 |
| http://localhost/history | 历史学习公开前台 |
| 登录后侧边栏 | 系统管理 + AI博客 + 微信运营 + 商城管理 + 历史学习 |

## 权限说明

- 用户/角色/菜单在 **系统管理** 中配置
- 博客权限标识：`blog:article:*`、`blog:ai:*` 等（见 `sql/blog_menu_seed.sql`）
- 测试角色 `blog_editor`（role_id=3）：可管文章，不可「博客智写」

## 目录

```
NovaMall/
├── backend/              # 纯后端（Java / Maven）
│   ├── ruoyi-admin/      # Spring Boot 启动模块
│   ├── ruoyi-framework/
│   ├── ruoyi-system/
│   ├── ruoyi-common/
│   ├── ruoyi-quartz/
│   ├── ruoyi-generator/
│   ├── ruoyi-blog/
│   ├── ruoyi-wechat/
│   ├── ruoyi-mall-product/
│   ├── ruoyi-mall-trade/
│   ├── ruoyi-mall-payment/
│   └── ruoyi-history/
├── frontend/             # 唯一前端；Dockerfile 与 nginx.conf 也在此目录
├── sql/                  # 数据库基线、升级与演示数据
├── docs/                 # 架构、实施与数据库升级说明
└── docker-compose.yml
```

> **说明**：系统管理、监控、代码生成等页面均在 `frontend/src/views/` 中维护。
