SET NAMES utf8mb4;
USE nova_mall;

-- AI 博客业务菜单与权限（menu_id 2000+，菜单格式对齐管理后台）
INSERT IGNORE INTO sys_menu VALUES
(2000, 'AI博客', 0, 5, 'blog-admin', NULL, '', '', 1, 0, 'M', '0', '0', '', 'documentation', 'admin', sysdate(), '', NULL, 'AI博客管理目录'),
(2001, '工作台', 2000, 1, 'dashboard', 'blog/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'blog:dashboard:view', 'chart', 'admin', sysdate(), '', NULL, ''),
(2002, '自己写', 2000, 2, 'article/edit', 'blog/article/edit', '', '', 1, 0, 'C', '0', '0', 'blog:article:add', 'edit', 'admin', sysdate(), '', NULL, ''),
(2003, '博客智写', 2000, 3, 'ai/write', 'blog/ai/write', '', 'BlogAiWrite', 1, 0, 'C', '0', '0', 'blog:ai:write', 'star', 'admin', sysdate(), '', NULL, ''),
(2004, '文章优化', 2000, 4, 'ai/optimize', 'blog/ai/optimize', '', 'BlogAiOptimize', 1, 0, 'C', '0', '0', 'blog:ai:optimize', 'clipboard', 'admin', sysdate(), '', NULL, ''),
(2005, '文章管理', 2000, 5, 'article', 'blog/article/index', '', '', 1, 0, 'C', '0', '0', 'blog:article:list', 'list', 'admin', sysdate(), '', NULL, ''),
(2006, '文章回收站', 2000, 6, 'article/recycle', 'blog/article/recycle', '', '', 1, 0, 'C', '0', '0', 'blog:article:recycle', 'row', 'admin', sysdate(), '', NULL, ''),
(2100, '文章查询', 2005, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:article:query', '#', 'admin', sysdate(), '', NULL, ''),
(2101, '文章新增', 2005, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:article:add', '#', 'admin', sysdate(), '', NULL, ''),
(2102, '文章修改', 2005, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:article:edit', '#', 'admin', sysdate(), '', NULL, ''),
(2103, '文章删除', 2005, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:article:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2110, '回收站查询', 2006, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:article:recycle', '#', 'admin', sysdate(), '', NULL, ''),
(2111, '文章恢复', 2006, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:article:restore', '#', 'admin', sysdate(), '', NULL, ''),
(2112, '彻底删除', 2006, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:article:purge', '#', 'admin', sysdate(), '', NULL, ''),
(2104, '图片上传', 2000, 7, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:upload:image', '#', 'admin', sysdate(), '', NULL, ''),
(2105, 'AI对话', 2000, 8, '#', '', '', '', 1, 0, 'F', '0', '0', 'blog:ai:chat', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id >= 2000 AND menu_id < 3000;

INSERT IGNORE INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark)
VALUES (3, '博客编辑者', 'blog_editor', 3, '1', '0', '0', 'admin', sysdate(), '可管理文章，不可使用 AI 智写');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(3, 2000), (3, 2001), (3, 2002), (3, 2005), (3, 2006),
(3, 2100), (3, 2101), (3, 2102), (3, 2104), (3, 2105), (3, 2110), (3, 2111);
