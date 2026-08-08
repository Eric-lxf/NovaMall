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
- HN 同步表、菜单和任务。

MySQL 官方镜像只会在数据目录为空时执行这些文件。已有 `mysql_data` 卷不会因代码更新而重新执行 SQL。

## 存量数据库手工升级

升级前先备份，并在维护窗口执行：

```bash
mysqldump --single-transaction -h <host> -u <user> -p nova_mall > nova_mall_before_upgrade.sql
```

先通过 `information_schema.COLUMNS`、`information_schema.TABLES` 或变更记录确认脚本尚未执行。`V2.1.0`、`V2.3.0`、`V2.6.0` 和支付约束脚本包含直接 `ADD COLUMN/INDEX`；`V2.5.0` 的属性选项也不适合重复播种。这些脚本均应视为**只执行一次**。

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
```

随后检查后端 `/actuator/health`。生产 Compose 只以该端点判断健康，不再以验证码接口作为降级条件，以免数据库异常被误判为健康。
