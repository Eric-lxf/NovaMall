# NovaMall

NovaMall 管理平台：完整系统管理 + 博客业务（文章 / 评论 / 上传 / AI / 账单识别），并向电商能力演进。

## 架构

前后端分离，**UI 只在仓库根目录 `frontend/`，`backend/` 为纯 Java 后端**。

- **backend**：Maven 多模块 + `ruoyi-blog` + `ruoyi-wechat` + `ruoyi-mall-*`（商品/交易/支付）
  - `ruoyi-admin`（启动入口）、`ruoyi-framework`、`ruoyi-system`、`ruoyi-common`、`ruoyi-quartz`、`ruoyi-generator`、`ruoyi-blog`、`ruoyi-mall-product`、`ruoyi-mall-trade`、`ruoyi-mall-payment`
- **frontend**：Vue3 + Element Plus，含管理后台、公开博客 `/blog`、公开商城 `/mall`
- **数据**：MySQL `nova_mall` + Redis（Token）

## 本地开发

### 1. 初始化数据库

按顺序执行 `sql/` 下脚本（或使用 Docker 自动初始化）：

```bash
mysql -u root -p < sql/00-init-db.sql
mysql -u root -p < sql/ry_base.sql
mysql -u root -p < sql/quartz.sql
mysql -u root -p < sql/blog_schema.sql
mysql -u root -p < sql/blog_comment_schema.sql
mysql -u root -p < sql/blog_notification_schema.sql
mysql -u root -p < sql/blog_notification_menu_fix.sql
mysql -u root -p < sql/blog_comment_menu_route_fix.sql
mysql -u root -p < sql/blog_analytics_schema.sql
mysql -u root -p < sql/blog_menu_seed.sql
mysql -u root -p < sql/blog_hn_schema.sql
mysql -u root -p < sql/blog_hn_menu_seed.sql
mysql -u root -p < sql/blog_hn_job_seed.sql
mysql -u root -p < sql/wechat_schema.sql
mysql -u root -p < sql/wechat_menu_route_fix.sql
mysql -u root -p < sql/ai_provider_schema.sql
mysql -u root -p < sql/ai_module_config_schema.sql
mysql -u root -p < sql/ai_provider_auth_mode.sql
mysql -u root -p < sql/mall_category_brand_schema.sql
mysql -u root -p < sql/mall_product_schema.sql
mysql -u root -p < sql/mall_attr_front_category_schema.sql
mysql -u root -p < sql/mall_phase_b_migrate_front_category.sql
# 若 migrate 中途失败导致 front 表半写入，先 TRUNCATE mall_front_category_rel / mall_front_category 再重跑 migrate
mysql -u root -p < sql/mall_address_schema.sql
mysql -u root -p < sql/mall_cart_order_schema.sql
mysql -u root -p < sql/mall_payment_schema.sql
mysql -u root -p < sql/mall_menu_seed.sql
mysql -u root -p < sql/mall_phase_b_menu_seed.sql
mysql -u root -p < sql/mall_menu_path_fix.sql
mysql -u root -p < sql/mall_order_job_seed.sql
# 可选演示数据：mysql -u root -p < sql/mall_demo_seed.sql
# 可选 Phase B 演示：mysql -u root -p < sql/mall_phase_b_attr_demo_seed.sql
```

> 后台商城菜单路由为 `/mall-admin/**`（如 `/mall-admin/spu`）；C 端商城为 `/mall`（如 `/mall`、`/mall/list`）。二者不可共用 `mall` 前缀，否则刷新后台页会 404。

修改 `backend/ruoyi-admin/src/main/resources/application-druid.yml` 中的数据库账号，库名建议 `nova_mall`。

### 2. 启动 Redis

Token 依赖 Redis，本地需运行 Redis（默认 `localhost:6379`）。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run -pl ruoyi-admin
```

默认账号：`admin` / `admin123`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

## Docker 构建（阿里云个人镜像仓库）

**构建上下文必须是仓库根目录 `NovaMall/`**，不能是 `frontend/` 子目录。

```bash
# 1. 登录私有仓库（否则会 unauthorized）
docker login crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com

# 2. 在仓库根目录构建
cd NovaMall
docker build -f frontend/Dockerfile -t crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/lxf_ai/nova-mall-web:latest .
docker build -f backend/Dockerfile -t crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/lxf_ai/nova-mall-server:latest .

# 3. 推送（构建成功后再 push）
docker push crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/lxf_ai/nova-mall-web:latest
docker push crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/lxf_ai/nova-mall-server:latest
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
# 可选：编辑 DEEPSEEK_API_KEY 作为回退；推荐登录后台「AI博客 → AI模型配置」添加多厂商 Key
docker compose --env-file .env up -d --build
# 或（云主机 / CI 推荐，失败会 exit 1 并打印后端日志）
chmod +x deploy.sh && ./deploy.sh
```

### 云部署报错 `ExitCode expect in [0] but is 1`

多为远程执行命令失败（阿里云 ECS 云助手、流水线等）。按顺序排查：

| 步骤 | 命令 | 说明 |
|------|------|------|
| 1 | `docker compose build backend 2>&1 \| tail -50` | Maven 构建失败：检查网络、是否上传完整 `backend/` |
| 2 | `docker compose build frontend 2>&1 \| tail -50` | 前端构建失败：需存在 `frontend/package-lock.json` |
| 3 | `docker compose logs mysql` | 初始化 SQL 失败：删卷重来 `docker compose down -v` |
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
| 登录后侧边栏 | 系统管理 + AI博客 |

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
│   └── ruoyi-blog/       # 博客业务扩展
├── frontend/             # 唯一前端（管理后台 + 博客页面）
├── sql/                  # 数据库初始化
├── docker/               # Dockerfile 与 Nginx
└── docker-compose.yml
```

> **说明**：系统管理、监控、代码生成等页面均在 `frontend/src/views/` 中维护。
