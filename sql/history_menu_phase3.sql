SET NAMES utf8mb4;
USE nova_mall;

-- 阶段3：抽取触发 + 知识审核菜单

INSERT IGNORE INTO sys_menu VALUES
(4007, '知识审核', 4000, 7, 'review', 'history/admin/review/index', '', '', 1, 0, 'C', '0', '0', 'history:claim:list', 'form', 'admin', sysdate(), '', NULL, ''),
(4142, '资料抽取', 4005, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:source:extract', '#', 'admin', sysdate(), '', NULL, ''),
(4160, '主张查询', 4007, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:claim:query', '#', 'admin', sysdate(), '', NULL, ''),
(4161, '主张审核', 4007, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:claim:audit', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (4007, 4142, 4160, 4161);
