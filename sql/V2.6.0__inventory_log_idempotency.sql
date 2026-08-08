SET NAMES utf8mb4;
USE nova_mall;

-- V2.6.0: 库存流水数据库幂等保护
-- 依赖: V2.4.0__inventory_log.sql
-- 说明: 历史流水保持 NULL；新版本为订单库存操作写入确定性幂等键。
--       业务代码先写入幂等键占位，再变更库存；唯一索引串行化同一业务操作。
-- 回滚: ALTER TABLE mall_inventory_log DROP INDEX uk_mall_inventory_log_idempotency,
--       DROP COLUMN idempotency_key;

ALTER TABLE `mall_inventory_log`
  ADD COLUMN `idempotency_key` varchar(191) DEFAULT NULL COMMENT '库存操作幂等键' AFTER `biz_id`,
  ADD UNIQUE KEY `uk_mall_inventory_log_idempotency` (`idempotency_key`);
