SET NAMES utf8mb4;
USE nova_mall;

-- V2.2.0: SKU 结构化规格表
-- 依赖: V2.1.0__sku_spec_key.sql
-- 回滚: DROP TABLE IF EXISTS mall_sku_spec;

CREATE TABLE IF NOT EXISTS `mall_sku_spec` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sku_id`      bigint       NOT NULL COMMENT 'SKU ID',
  `attr_id`     bigint       NOT NULL COMMENT '属性ID',
  `option_id`   bigint       DEFAULT NULL COMMENT '选项ID（枚举类属性）',
  `value`       varchar(128) NOT NULL DEFAULT '' COMMENT '展示值快照',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_sku_spec_sku_attr` (`sku_id`, `attr_id`),
  KEY `idx_mall_sku_spec_attr_option` (`attr_id`, `option_id`),
  KEY `idx_mall_sku_spec_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU结构化规格';
