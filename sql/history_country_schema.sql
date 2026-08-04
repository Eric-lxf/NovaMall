SET NAMES utf8mb4;
USE nova_mall;

-- 国家/文明维度：支持 中国→秦朝、埃及→新王国、美国→内战时期 等浏览

CREATE TABLE IF NOT EXISTS `history_country` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '国家/文明ID',
  `name`         varchar(64)  NOT NULL COMMENT '名称，如中国、埃及、美国',
  `alias`        varchar(128) DEFAULT NULL COMMENT '别名',
  `region`       varchar(64)  DEFAULT NULL COMMENT '区域，如东亚/北非/北美',
  `period_label` varchar(32)  NOT NULL DEFAULT '时期' COMMENT '时期称呼：朝代/王朝/时期',
  `summary`      varchar(1000) DEFAULT NULL COMMENT '简介',
  `sort`         int          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`       char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`    varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`       varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_history_country_name` (`name`),
  KEY `idx_history_country_region` (`region`),
  KEY `idx_history_country_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史国家/文明';

-- 存量库：为时期表增加所属国家（可重复执行）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'history_period'
    AND COLUMN_NAME = 'country_id'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `history_period` ADD COLUMN `country_id` bigint DEFAULT NULL COMMENT ''所属国家/文明'' AFTER `alias`, ADD KEY `idx_history_period_country` (`country_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
