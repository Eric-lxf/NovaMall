SET NAMES utf8mb4;
USE nova_mall;

CREATE TABLE IF NOT EXISTS `blog_hn_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `hn_id` bigint NOT NULL COMMENT 'HN 条目 ID',
  `item_type` varchar(32) DEFAULT NULL COMMENT 'story/job/...',
  `title_en` varchar(512) DEFAULT NULL,
  `title_zh` varchar(512) DEFAULT NULL,
  `url` varchar(1024) DEFAULT NULL,
  `hn_url` varchar(256) NOT NULL,
  `text_en` mediumtext,
  `text_zh` mediumtext,
  `summary_zh` varchar(1000) DEFAULT NULL,
  `score` int DEFAULT 0,
  `author` varchar(64) DEFAULT NULL,
  `comment_count` int DEFAULT 0,
  `hn_time` datetime DEFAULT NULL COMMENT 'HN 发布时间(东八区换算后)',
  `fetched_at` datetime DEFAULT NULL,
  `translated_at` datetime DEFAULT NULL,
  `translate_status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|ok|fail',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0未发布 1已发布',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hn_id` (`hn_id`),
  KEY `idx_status_translate` (`status`, `translate_status`),
  KEY `idx_fetched_at` (`fetched_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HN 帖缓存';

CREATE TABLE IF NOT EXISTS `blog_hn_rank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `board` varchar(16) NOT NULL COMMENT 'news|past|show|jobs',
  `hn_id` bigint NOT NULL,
  `rank` int NOT NULL,
  `snapshot_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_board_snapshot` (`board`, `snapshot_at`),
  KEY `idx_board_hn` (`board`, `hn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HN 榜单快照';

INSERT INTO `ai_prompt_template` (`template_name`, `scene_type`, `system_prompt`, `model_name`, `temperature`, `is_active`)
SELECT 'HN 中英翻译', 'TRANSLATE',
  '你是中英技术内容翻译助手。将用户给出的 Hacker News 标题/正文译为流畅简体中文。严格只输出 JSON 对象，字段：title_zh（必填）、summary_zh（1～2 句中文摘要）、text_zh（若无英文正文则空字符串）。不要解释、不要 Markdown 代码围栏。',
  'deepseek-chat', 0.30, 1
WHERE NOT EXISTS (SELECT 1 FROM `ai_prompt_template` WHERE `scene_type` = 'TRANSLATE' LIMIT 1);
