# NovaMall 数据库初始化与升级

本文档是数据库发布入口。生产部署工作流**不会自动执行仓库 SQL**：当前脚本中包含非幂等 `ALTER TABLE`，在没有迁移版本表之前，自动重放可能中断发布或破坏存量数据。

## 新建空数据库

推荐复制环境文件后，由 Docker Compose 初始化空库：

```bash
cp .env.example .env
# 修改 MYSQL_ROOT_PASSWORD 和 TOKEN_SECRET；TOKEN_SECRET 可用 openssl rand -hex 64 生成
docker compose --env-file .env up mysql -d
docker compose logs -f mysql
```

初始化顺序以根目录 `docker-compose.yml` 中 `mysql.volumes` 映射到 `/docker-entrypoint-initdb.d/` 的编号为准，当前包括：

- RuoYi、Quartz、博客、微信和 AI 基线；
- Product Phase B、SKU 规格、库存字段/流水和 3C 属性；
- Payment 有效支付单约束；
- History 最终基线、AI 抽取配置、菜单和本地演示数据；
- HN 同步表、菜单和任务；
- 外部博客写入 API 客户端、幂等、审计和文章来源字段。

MySQL 官方镜像只会在数据目录为空时执行这些文件。已有 `mysql_data` 卷不会因代码更新而重新执行 SQL。

## 存量数据库手工升级

升级前先备份，并在维护窗口执行：

```bash
mysqldump --single-transaction -h <host> -u <user> -p nova_mall > nova_mall_before_upgrade.sql
```

先通过 `information_schema.COLUMNS`、`information_schema.TABLES` 或变更记录确认脚本尚未执行。`V2.1.0`、`V2.3.0`、`V2.6.0`、`V2.7.0` 和支付约束脚本包含直接 `ADD COLUMN/INDEX`；`V2.5.0` 的属性选项也不适合重复播种。这些脚本均应视为**只执行一次**。

### Product / Inventory / Payment

按当前数据库所处版本，从缺失的第一项开始顺序执行：

```bash
mysql -h <host> -u <user> -p nova_mall < sql/mall_attr_front_category_schema.sql
mysql -h <host> -u <user> -p nova_mall < sql/V2.1.0__sku_spec_key.sql
mysql -h <host> -u <user> -p nova_mall < sql/V2.2.0__sku_spec_normalized.sql
mysql -h <host> -u <user> -p nova_mall < sql/V2.3.0__inventory_fields.sql
mysql -h <host> -u <user> -p nova_mall < sql/V2.4.0__inventory_log.sql
mysql -h <host> -u <user> -p nova_mall < sql/V2.4.2__payment_active_order_guard.sql
mysql -h <host> -u <user> -p nova_mall < sql/V2.5.0__product_3c_attr_seed.sql
mysql -h <host> -u <user> -p nova_mall < sql/V2.6.0__inventory_log_idempotency.sql
```

`mall_phase_b_migrate_front_category.sql` 只用于尚未迁移前台类目的旧库；它不是通用的重复升级脚本。执行前应确认 `mall_front_category` 与 `mall_front_category_rel` 的数据状态。

`mall_demo_seed.sql`、`mall_phase_b_attr_demo_seed.sql` 是本地联调数据，不应导入生产库。

### Blog External Write API

外部博客写入 API 默认由 `BLOG_EXTERNAL_API_ENABLED=false` 关闭。由于新后端的文章模型已经包含来源字段，即使公开 API 保持关闭，也必须在发布相关后端代码前先备份数据库，并在维护窗口执行一次：

```bash
mysql -h <host> -u <user> -p nova_mall < sql/V2.7.0__blog_external_write_api.sql
```

该迁移创建 `blog_api_client`、`blog_api_idempotency`、`blog_api_audit`，为 `blog_article` 增加来源字段和唯一索引，并安装“外部API客户端”管理菜单。迁移不创建默认客户端，也不写入明文密钥；客户端 secret 仅在管理端创建或轮换时返回一次，数据库只保存 BCrypt hash。

迁移完成后先保持功能关闭，完成下方结构检查后把生产变量 `BLOG_EXTERNAL_API_SCHEMA_READY` 设为 `true` 并部署。应用会再次以只读方式校验三张表、`blog_article` 三个来源列和关键唯一索引；结构不完整时启动失败。随后通过管理端创建客户端，确认 TLS/受信代理配置后，再将 `BLOG_EXTERNAL_API_ENABLED` 改为 `true` 并重新部署。关闭公开 API 时保留 `BLOG_EXTERNAL_API_SCHEMA_READY=true`，让审计与幂等清理任务继续运行。允许的 scope 固定为：

- `blog.article.create`
- `blog.article.read.own`
- `blog.taxonomy.read`

回滚应用时只需将功能开关恢复为 `false`；不要在紧急回滚中删除审计、幂等或文章来源字段。

### History

`history_schema.sql` 是当前完整结构基线；对于已经执行过早期 History 脚本的数据库，再执行兼容升级脚本补齐国家维度：

```bash
mysql -h <host> -u <user> -p nova_mall < sql/history_schema.sql
mysql -h <host> -u <user> -p nova_mall < sql/history_country_schema.sql
mysql -h <host> -u <user> -p nova_mall < sql/history_ai_extract_schema.sql
mysql -h <host> -u <user> -p nova_mall < sql/history_menu_seed.sql
```

以下仅为演示数据，生产环境默认不执行：

```bash
mysql -h <host> -u <user> -p nova_mall < sql/history_demo_seed.sql
mysql -h <host> -u <user> -p nova_mall < sql/history_demo_country.sql
mysql -h <host> -u <user> -p nova_mall < sql/history_demo_phase4.sql
```

## 发布后的检查

```sql
SHOW TABLES LIKE 'mall_sku_spec';
SHOW TABLES LIKE 'mall_inventory_log';
SHOW INDEX FROM mall_inventory_log WHERE Key_name = 'uk_mall_inventory_log_idempotency';
SHOW TABLES LIKE 'history_country';
SHOW TABLES LIKE 'history_learning_unit';
SHOW INDEX FROM mall_payment_order WHERE Key_name = 'uk_mall_pay_order_active';
SHOW TABLES LIKE 'blog_api_client';
SHOW TABLES LIKE 'blog_api_idempotency';
SHOW TABLES LIKE 'blog_api_audit';
SHOW INDEX FROM blog_api_client WHERE Key_name = 'uk_blog_api_client_client_id';
SHOW INDEX FROM blog_api_idempotency WHERE Key_name = 'uk_blog_api_idempotency_client_key';
SHOW INDEX FROM blog_article WHERE Key_name = 'uk_blog_article_source_external';
SHOW INDEX FROM blog_api_audit WHERE Key_name = 'idx_blog_api_audit_request_id';
```

随后检查后端 `/actuator/health`。生产 Compose 只以该端点判断健康，不再以验证码接口作为降级条件，以免数据库异常被误判为健康。
