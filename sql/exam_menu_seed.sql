-- Run serially, after checking that /exam is not owned by another feature.
-- Auto-increment menu IDs avoid fixed-ID collisions. Existing menus/role grants are not overwritten.
-- No role grants are added: the platform super-admin can assign the new permissions explicitly.
START TRANSACTION;

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '智能命题',0,7,'exam',NULL,1,1,'M','0','0','','education','admin',NOW(),'智能命题：须完成迁移并显式启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='exam');

SET @exam_root_id = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id=0 AND path='exam' AND menu_type='M' AND menu_name='智能命题');

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '任务中心',@exam_root_id,1,'task','exam/task/index',1,1,'C','0','0','exam:task:list','job','admin',NOW(),'仅显示当前用户任务'
WHERE @exam_root_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@exam_root_id AND path='task');

SET @exam_task_menu_id = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id=@exam_root_id AND path='task' AND component='exam/task/index');

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT desired.label,@exam_task_menu_id,desired.sort_order,'#','',1,1,'F','0','0',desired.permission,'#','admin',NOW(),'命题任务操作权限'
FROM (
    SELECT '创建基础自检' label,1 sort_order,'exam:task:create' permission
    UNION ALL SELECT '取消任务',2,'exam:task:cancel'
    UNION ALL SELECT '重试基础自检',3,'exam:task:retry'
) desired
WHERE @exam_task_menu_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms=desired.permission);

COMMIT;

-- NULL indicates an existing route conflict: stop and review, do not rename/delete unrelated menus.
SELECT @exam_root_id AS exam_root_id, @exam_task_menu_id AS exam_task_menu_id;
