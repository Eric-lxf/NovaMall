SET NAMES utf8mb4;
USE nova_mall;

-- HN 内容管理菜单（挂在 AI博客 2000 下，menu_id 2050 / 2230-2231）
INSERT IGNORE INTO sys_menu VALUES
(2050, 'HN 内容', 2000, 10, 'hn', 'blog/hn/index', '', '', 1, 0, 'C', '0', '0', 'blog:hn:list', 'guide', 'admin', sysdate(), '', NULL, ''),
(2230, 'HN 查询', 2050, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:hn:query', '#', 'admin', sysdate(), '', NULL, ''),
(2231, 'HN 同步', 2050, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:hn:sync', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 2050), (1, 2230), (1, 2231);
