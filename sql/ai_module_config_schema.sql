CREATE TABLE IF NOT EXISTS `ai_module_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module_code` varchar(32) NOT NULL COMMENT 'editor/write/optimize/comment_moderate/bill_vision/bill_advice/history_extract',
  `provider_id` bigint NOT NULL COMMENT 'ai_provider.id',
  `text_model` varchar(128) DEFAULT NULL COMMENT '文本模型覆盖，空则 provider.default_model',
  `vision_model` varchar(128) DEFAULT NULL COMMENT '视觉模型覆盖，空则 provider.vision_model/default_model',
  `temperature` decimal(3,2) DEFAULT NULL COMMENT '温度覆盖，空则提示词模板温度',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_module_config_code` (`module_code`),
  KEY `idx_ai_module_config_provider` (`provider_id`),
  CONSTRAINT `chk_ai_module_config_module_code` CHECK (`module_code` IN ('editor', 'write', 'optimize', 'comment_moderate', 'bill_vision', 'bill_advice', 'history_extract'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 功能模块模型配置';
