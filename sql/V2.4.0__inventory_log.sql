SET NAMES utf8mb4;
USE nova_mall;

-- V2.4.0: 库存流水
-- 依赖: V2.3.0__inventory_fields.sql
-- 回滚: DROP TABLE IF EXISTS mall_inventory_log;

CREATE TABLE IF NOT EXISTS `mall_inventory_log` (
  `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sku_id`           bigint       NOT NULL COMMENT 'SKU ID',
  `change_type`      varchar(32)  NOT NULL COMMENT '变更类型',
  `quantity`         int          NOT NULL COMMENT '变更数量（正数）',
  `before_total`     int          NOT NULL DEFAULT 0 COMMENT '变更前实际库存',
  `after_total`      int          NOT NULL DEFAULT 0 COMMENT '变更后实际库存',
  `before_locked`    int          NOT NULL DEFAULT 0 COMMENT '变更前锁定库存',
  `after_locked`     int          NOT NULL DEFAULT 0 COMMENT '变更后锁定库存',
  `before_available` int          NOT NULL DEFAULT 0 COMMENT '变更前可售库存',
  `after_available`  int          NOT NULL DEFAULT 0 COMMENT '变更后可售库存',
  `biz_type`         varchar(32)  DEFAULT NULL COMMENT '业务类型 ORDER/ADJUST 等',
  `biz_id`           varchar(64)  DEFAULT NULL COMMENT '业务单号',
  `operator`         varchar(64)  DEFAULT '' COMMENT '操作人',
  `remark`           varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time`      datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mall_inventory_log_sku` (`sku_id`),
  KEY `idx_mall_inventory_log_biz` (`biz_type`, `biz_id`),
  KEY `idx_mall_inventory_log_type` (`change_type`),
  KEY `idx_mall_inventory_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城库存流水';
