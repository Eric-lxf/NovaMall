SET NAMES utf8mb4;
USE nova_mall;

-- V2.4.2: 每个业务订单最多保留一个非终态支付单。
-- 依赖: mall_payment_schema.sql
-- MySQL 唯一索引允许多个 NULL，因此终态支付单 active_flag=NULL 可保留历史记录。

ALTER TABLE `mall_payment_order`
  ADD COLUMN `active_flag` tinyint DEFAULT NULL COMMENT '有效支付单标志：1有效，终态NULL' AFTER `status`;

-- 同一订单历史上若有多条非终态支付单，只保留最新一条，其余关闭。
UPDATE `mall_payment_order` older
JOIN `mall_payment_order` newer
  ON newer.`order_id` = older.`order_id`
  AND newer.`id` > older.`id`
  AND newer.`status` IN ('INIT', 'PAYING')
SET older.`status` = 'CLOSED', older.`active_flag` = NULL
WHERE older.`status` IN ('INIT', 'PAYING');

UPDATE `mall_payment_order`
SET `active_flag` = 1
WHERE `status` IN ('INIT', 'PAYING');

ALTER TABLE `mall_payment_order`
  ADD UNIQUE KEY `uk_mall_pay_order_active` (`order_id`, `active_flag`);
