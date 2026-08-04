SET NAMES utf8mb4;
USE nova_mall;

-- 国家管理菜单（menu_id 4010 / 4190+）

INSERT IGNORE INTO sys_menu VALUES
(4010, '国家管理', 4000, 0, 'country', 'history/admin/country/index', '', '', 1, 0, 'C', '0', '0', 'history:country:list', 'international', 'admin', sysdate(), '', NULL, ''),

(4190, '国家查询', 4010, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:country:query', '#', 'admin', sysdate(), '', NULL, ''),
(4191, '国家新增', 4010, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:country:add', '#', 'admin', sysdate(), '', NULL, ''),
(4192, '国家修改', 4010, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:country:edit', '#', 'admin', sysdate(), '', NULL, ''),
(4193, '国家删除', 4010, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:country:remove', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (4010, 4190, 4191, 4192, 4193);
