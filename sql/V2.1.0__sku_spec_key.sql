SET NAMES utf8mb4;
USE nova_mall;

-- V2.1.0: SKU 规范化规格键
-- 依赖: mall_product_schema.sql, mall_attr_front_category_schema.sql
-- 回滚: DROP INDEX uk_mall_sku_spu_spec_key ON mall_sku; ALTER TABLE mall_sku DROP COLUMN spec_key;

ALTER TABLE `mall_sku`
  ADD COLUMN `spec_key` varchar(512) NOT NULL DEFAULT '' COMMENT '规范化规格组合 attr:id=option:id|...' AFTER `specs_json`;

-- 历史数据先占位，避免唯一索引冲突（同一 SPU 多空规格）
UPDATE `mall_sku`
SET `spec_key` = CONCAT('legacy:', `id`)
WHERE `spec_key` = '' OR `spec_key` IS NULL;

ALTER TABLE `mall_sku`
  ADD UNIQUE KEY `uk_mall_sku_spu_spec_key` (`spu_id`, `spec_key`);
