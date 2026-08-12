-- External blog write API: clients, idempotency, audit and article source tracking.
-- This migration is additive and must be executed once before BLOG_EXTERNAL_API_ENABLED=true.

SET NAMES utf8mb4;
USE nova_mall;

CREATE TABLE `blog_api_client` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '公开客户端标识',
  `client_name` varchar(100) NOT NULL COMMENT '客户端名称',
  `client_secret_hash` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'BCrypt 密文摘要，不保存明文 secret',
  `secret_version` int NOT NULL DEFAULT 1 COMMENT '密钥版本，轮换后递增',
  `scopes` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '逗号分隔权限: blog.article.create,blog.article.read.own,blog.taxonomy.read',
  `status` char(1) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '0' COMMENT '状态: 0-启用, 1-停用',
  `rate_limit_per_minute` int NOT NULL DEFAULT 60 COMMENT '每分钟请求上限',
  `token_ttl_seconds` int NOT NULL DEFAULT 900 COMMENT '访问令牌有效期（秒）',
  `last_used_time` datetime DEFAULT NULL COMMENT '最后成功使用时间',
  `secret_rotated_time` datetime DEFAULT NULL COMMENT '最后密钥轮换时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blog_api_client_client_id` (`client_id`),
  KEY `idx_blog_api_client_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部博客 API 客户端';

CREATE TABLE `blog_api_idempotency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_id` bigint NOT NULL COMMENT 'API 客户端主键',
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方幂等键',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求体 SHA-256',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0-处理中, 1-成功, 2-失败',
  `article_id` bigint DEFAULT NULL COMMENT '成功创建的文章ID',
  `http_status` smallint DEFAULT NULL COMMENT '首次响应 HTTP 状态',
  `response_body` varchar(2000) DEFAULT NULL COMMENT '可安全重放的首次响应，不保存文章正文',
  `expire_time` datetime NOT NULL COMMENT '幂等记录过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blog_api_idempotency_client_key` (`client_id`, `idempotency_key`),
  KEY `idx_blog_api_idempotency_expire` (`expire_time`),
  KEY `idx_blog_api_idempotency_article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部博客 API 幂等记录';

CREATE TABLE `blog_api_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全链路请求ID',
  `client_id` bigint DEFAULT NULL COMMENT 'API 客户端主键，认证失败时可为空',
  `secret_version` int DEFAULT NULL COMMENT '请求所用密钥版本',
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '幂等键',
  `request_method` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'HTTP 方法',
  `request_path` varchar(255) NOT NULL COMMENT '请求路径',
  `source_ip` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '可信代理解析后的来源IP',
  `request_body_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '请求体 SHA-256，不保存正文',
  `auth_result` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '认证/授权结果',
  `http_status` smallint NOT NULL COMMENT 'HTTP 响应状态',
  `error_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '稳定错误码',
  `article_id` bigint DEFAULT NULL COMMENT '关联文章ID',
  `cost_time_ms` bigint DEFAULT NULL COMMENT '处理耗时（毫秒）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_blog_api_audit_request_id` (`request_id`),
  KEY `idx_blog_api_audit_client_time` (`client_id`, `create_time`),
  KEY `idx_blog_api_audit_created` (`create_time`),
  KEY `idx_blog_api_audit_article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部博客 API 安全审计';

ALTER TABLE `blog_article`
  ADD COLUMN `source_type` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ADMIN' COMMENT '来源: ADMIN/EXTERNAL_API' AFTER `author_user_id`,
  ADD COLUMN `source_client_id` bigint DEFAULT NULL COMMENT '外部 API 客户端主键' AFTER `source_type`,
  ADD COLUMN `external_id` varchar(128) DEFAULT NULL COMMENT '调用方文章标识' AFTER `source_client_id`,
  ADD UNIQUE KEY `uk_blog_article_source_external` (`source_client_id`, `external_id`),
  ADD KEY `idx_blog_article_source_client` (`source_type`, `source_client_id`, `is_deleted`, `update_time`, `id`);

-- 管理端菜单：放在“AI博客”目录下，仅授予 admin 角色。
INSERT IGNORE INTO `sys_menu` VALUES
(2440, '外部API客户端', 2000, 10, 'external-api', 'blog/external-api/client/index', '', 'BlogExternalApiClient', 1, 0, 'C', '0', '0', 'blog:external-api:client:list', 'key', 'admin', sysdate(), '', NULL, '管理外部博客写入 API 客户端'),
(2441, '客户端查询', 2440, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:external-api:client:list', '#', 'admin', sysdate(), '', NULL, ''),
(2442, '客户端新增', 2440, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:external-api:client:add', '#', 'admin', sysdate(), '', NULL, ''),
(2443, '客户端修改', 2440, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:external-api:client:edit', '#', 'admin', sysdate(), '', NULL, ''),
(2444, '密钥轮换', 2440, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:external-api:client:rotate', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 2440 AND 2444;
