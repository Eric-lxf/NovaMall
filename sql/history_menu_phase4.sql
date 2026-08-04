SET NAMES utf8mb4;
USE nova_mall;

-- 阶段 4：学习单元 / 学习路径菜单与权限

INSERT IGNORE INTO sys_menu VALUES
(4008, '学习单元', 4000, 8, 'unit', 'history/admin/unit/index', '', '', 1, 0, 'C', '0', '0', 'history:unit:list', 'education', 'admin', sysdate(), '', NULL, ''),
(4009, '学习路径', 4000, 9, 'path', 'history/admin/path/index', '', '', 1, 0, 'C', '0', '0', 'history:path:list', 'guide', 'admin', sysdate(), '', NULL, ''),

(4170, '单元查询', 4008, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:unit:query', '#', 'admin', sysdate(), '', NULL, ''),
(4171, '单元新增', 4008, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:unit:add', '#', 'admin', sysdate(), '', NULL, ''),
(4172, '单元修改', 4008, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:unit:edit', '#', 'admin', sysdate(), '', NULL, ''),
(4173, '单元删除', 4008, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:unit:remove', '#', 'admin', sysdate(), '', NULL, ''),
(4174, '单元发布', 4008, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:unit:publish', '#', 'admin', sysdate(), '', NULL, ''),

(4180, '路径查询', 4009, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:path:query', '#', 'admin', sysdate(), '', NULL, ''),
(4181, '路径新增', 4009, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:path:add', '#', 'admin', sysdate(), '', NULL, ''),
(4182, '路径修改', 4009, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:path:edit', '#', 'admin', sysdate(), '', NULL, ''),
(4183, '路径删除', 4009, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:path:remove', '#', 'admin', sysdate(), '', NULL, ''),
(4184, '路径发布', 4009, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:path:publish', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (4008, 4009, 4170, 4171, 4172, 4173, 4174, 4180, 4181, 4182, 4183, 4184);
