SET NAMES utf8mb4;
USE nova_mall;

-- 阶段2：资料解析重试权限
INSERT IGNORE INTO sys_menu VALUES
(4151, '任务重试', 4006, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:task:retry', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 4151);
