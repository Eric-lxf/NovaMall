SET NAMES utf8mb4;
USE nova_mall;

-- 新版 blog_schema.sql 已包含 author_user_id；兼容尚未执行过该升级的存量库，
-- 同时避免空库初始化时重复 ADD COLUMN 中断后续脚本。
SET @author_user_id_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'blog_article'
    AND COLUMN_NAME = 'author_user_id'
);
SET @add_author_user_id_sql := IF(
  @author_user_id_exists = 0,
  'ALTER TABLE blog_article ADD COLUMN author_user_id bigint DEFAULT NULL COMMENT ''作者用户ID'' AFTER category_id',
  'SELECT 1'
);
PREPARE add_author_user_id_stmt FROM @add_author_user_id_sql;
EXECUTE add_author_user_id_stmt;
DEALLOCATE PREPARE add_author_user_id_stmt;

UPDATE blog_article SET author_user_id = 1 WHERE author_user_id IS NULL;

CREATE TABLE IF NOT EXISTS blog_user_notification (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint NOT NULL COMMENT '接收人',
  type varchar(20) NOT NULL COMMENT 'COMMENT/REPLY/SYSTEM',
  title varchar(200) NOT NULL,
  content varchar(1000) NOT NULL,
  link_url varchar(500) DEFAULT NULL,
  biz_type varchar(30) DEFAULT NULL,
  biz_id bigint DEFAULT NULL,
  is_read tinyint NOT NULL DEFAULT 0,
  read_time datetime DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_read_time (user_id, is_read, create_time),
  KEY idx_user_biz (user_id, type, biz_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户站内通知';

CREATE TABLE IF NOT EXISTS blog_notification_preference (
  user_id bigint NOT NULL,
  enable_in_app tinyint NOT NULL DEFAULT 1,
  enable_email tinyint NOT NULL DEFAULT 1,
  enable_comment tinyint NOT NULL DEFAULT 1,
  enable_reply tinyint NOT NULL DEFAULT 1,
  enable_system tinyint NOT NULL DEFAULT 1,
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知偏好';

CREATE TABLE IF NOT EXISTS blog_email_outbox (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint DEFAULT NULL,
  to_email varchar(128) NOT NULL,
  subject varchar(200) NOT NULL,
  body text NOT NULL,
  status tinyint NOT NULL DEFAULT 0 COMMENT '0待发送 1成功 2失败',
  retry_count int NOT NULL DEFAULT 0,
  error_message varchar(500) DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_time datetime DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件发件队列';

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '博客通知总开关', 'blog.notification.enabled', 'true', 'Y', 'admin', sysdate(), '关闭后不再产生新通知'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'blog.notification.enabled');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '博客邮件通知', 'blog.notification.email.enabled', 'false', 'Y', 'admin', sysdate(), '需配置 spring.mail'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'blog.notification.email.enabled');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '博客前台地址', 'blog.notification.publicBaseUrl', 'http://localhost', 'Y', 'admin', sysdate(), '邮件跳转前缀'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'blog.notification.publicBaseUrl');

INSERT IGNORE INTO sys_menu VALUES
(2040, '消息中心', 2030, 5, 'notification', 'blog/notification/index', '', 'BlogNotification', 1, 0, 'C', '0', '0', 'blog:notification:list', 'message', 'admin', sysdate(), '', NULL, '站内消息'),
(2041, '发送系统通知', 2030, 6, 'notification-send', 'blog/notification/send', '', 'BlogNotificationSend', 1, 0, 'C', '0', '0', 'blog:notification:send', 'email', 'admin', sysdate(), '', NULL, ''),
(2220, '消息查询', 2040, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:notification:list', '#', 'admin', sysdate(), '', NULL, ''),
(2221, '系统通知发送', 2041, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:notification:send', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (2040, 2041, 2220, 2221);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(3, 2040), (3, 2220);
