SET NAMES utf8mb4;
USE nova_mall;

-- V2.3.0: SKU 可售/锁定/实际库存
-- 依赖: mall_product_schema.sql
-- 回滚: ALTER TABLE mall_sku DROP COLUMN stock_total, DROP COLUMN stock_locked, DROP COLUMN stock_available, DROP COLUMN stock_warning;
-- 说明: 短期保留旧 stock 字段，与 stock_available 双写兼容

ALTER TABLE `mall_sku`
  ADD COLUMN `stock_total` int NOT NULL DEFAULT 0 COMMENT '实际库存' AFTER `stock`,
  ADD COLUMN `stock_locked` int NOT NULL DEFAULT 0 COMMENT '锁定库存' AFTER `stock_total`,
  ADD COLUMN `stock_available` int NOT NULL DEFAULT 0 COMMENT '可售库存' AFTER `stock_locked`,
  ADD COLUMN `stock_warning` int NOT NULL DEFAULT 0 COMMENT '库存预警值' AFTER `stock_available`;

UPDATE `mall_sku`
SET `stock_total` = IFNULL(`stock`, 0),
    `stock_locked` = 0,
    `stock_available` = IFNULL(`stock`, 0)
WHERE 1 = 1;
