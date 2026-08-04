SET NAMES utf8mb4;
USE nova_mall;

-- 历史学习平台后台菜单（menu_id 4000–4999，避开商城 3000+ / 博客 2000+）
-- path=history-admin，避免与 C 端 /history 冲突

INSERT IGNORE INTO sys_menu VALUES
(4000, '历史学习', 0, 7, 'history-admin', NULL, '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '', NULL, '历史学习管理目录'),
(4001, '时期管理', 4000, 1, 'period', 'history/admin/period/index', '', '', 1, 0, 'C', '0', '0', 'history:period:list', 'time', 'admin', sysdate(), '', NULL, ''),
(4002, '事件管理', 4000, 2, 'event', 'history/admin/event/index', '', '', 1, 0, 'C', '0', '0', 'history:event:list', 'list', 'admin', sysdate(), '', NULL, ''),
(4003, '人物管理', 4000, 3, 'person', 'history/admin/person/index', '', '', 1, 0, 'C', '0', '0', 'history:person:list', 'peoples', 'admin', sysdate(), '', NULL, ''),
(4004, '地点管理', 4000, 4, 'place', 'history/admin/place/index', '', '', 1, 0, 'C', '0', '0', 'history:place:list', 'guide', 'admin', sysdate(), '', NULL, ''),
(4005, '资料管理', 4000, 5, 'source', 'history/admin/source/index', '', '', 1, 0, 'C', '0', '0', 'history:source:list', 'documentation', 'admin', sysdate(), '', NULL, ''),
(4006, 'AI任务', 4000, 6, 'task', 'history/admin/task/index', '', '', 1, 0, 'C', '0', '0', 'history:task:list', 'job', 'admin', sysdate(), '', NULL, ''),
(4007, '知识审核', 4000, 7, 'review', 'history/admin/review/index', '', '', 1, 0, 'C', '0', '0', 'history:claim:list', 'form', 'admin', sysdate(), '', NULL, ''),
(4008, '学习单元', 4000, 8, 'unit', 'history/admin/unit/index', '', '', 1, 0, 'C', '0', '0', 'history:unit:list', 'education', 'admin', sysdate(), '', NULL, ''),
(4009, '学习路径', 4000, 9, 'path', 'history/admin/path/index', '', '', 1, 0, 'C', '0', '0', 'history:path:list', 'guide', 'admin', sysdate(), '', NULL, ''),

(4100, '时期查询', 4001, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:period:query', '#', 'admin', sysdate(), '', NULL, ''),
(4101, '时期新增', 4001, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:period:add', '#', 'admin', sysdate(), '', NULL, ''),
(4102, '时期修改', 4001, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:period:edit', '#', 'admin', sysdate(), '', NULL, ''),
(4103, '时期删除', 4001, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:period:remove', '#', 'admin', sysdate(), '', NULL, ''),

(4110, '事件查询', 4002, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:event:query', '#', 'admin', sysdate(), '', NULL, ''),
(4111, '事件新增', 4002, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:event:add', '#', 'admin', sysdate(), '', NULL, ''),
(4112, '事件修改', 4002, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:event:edit', '#', 'admin', sysdate(), '', NULL, ''),
(4113, '事件删除', 4002, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:event:remove', '#', 'admin', sysdate(), '', NULL, ''),
(4114, '事件发布', 4002, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:event:publish', '#', 'admin', sysdate(), '', NULL, ''),

(4120, '人物查询', 4003, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:person:query', '#', 'admin', sysdate(), '', NULL, ''),
(4121, '人物新增', 4003, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:person:add', '#', 'admin', sysdate(), '', NULL, ''),
(4122, '人物修改', 4003, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:person:edit', '#', 'admin', sysdate(), '', NULL, ''),
(4123, '人物删除', 4003, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:person:remove', '#', 'admin', sysdate(), '', NULL, ''),

(4130, '地点查询', 4004, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:place:query', '#', 'admin', sysdate(), '', NULL, ''),
(4131, '地点新增', 4004, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:place:add', '#', 'admin', sysdate(), '', NULL, ''),
(4132, '地点修改', 4004, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:place:edit', '#', 'admin', sysdate(), '', NULL, ''),
(4133, '地点删除', 4004, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:place:remove', '#', 'admin', sysdate(), '', NULL, ''),

(4140, '资料查询', 4005, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:source:query', '#', 'admin', sysdate(), '', NULL, ''),
(4141, '资料导入', 4005, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:source:import', '#', 'admin', sysdate(), '', NULL, ''),
(4142, '资料抽取', 4005, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:source:extract', '#', 'admin', sysdate(), '', NULL, ''),

(4150, '任务查询', 4006, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:task:query', '#', 'admin', sysdate(), '', NULL, ''),
(4151, '任务重试', 4006, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:task:retry', '#', 'admin', sysdate(), '', NULL, ''),

(4160, '主张查询', 4007, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:claim:query', '#', 'admin', sysdate(), '', NULL, ''),
(4161, '主张审核', 4007, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'history:claim:audit', '#', 'admin', sysdate(), '', NULL, ''),

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
SELECT 1, menu_id FROM sys_menu WHERE menu_id >= 4000 AND menu_id < 5000;
