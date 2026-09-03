-- 智能命题：目录与任务中心菜单，执行顺序为四份命题脚本中的第 3 份。
-- 先完成两份建表脚本，备份并选中目标数据库；确认根路由 /exam 未被其他功能占用。
-- 必须串行执行；菜单编号使用自增主键，已有菜单及角色授权不会被覆盖。
-- 本脚本仅向系统菜单表插入缺失记录，不创建或修改该表结构，不自动授予角色权限。
-- 菜单类型：M为目录、C为页面、F为按钮；显示和状态字段的0表示显示、正常。
START TRANSACTION;

-- 创建智能命题根目录；同一路径已存在时不插入，避免抢占其他功能路由。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '智能命题',0,7,'exam',NULL,1,1,'M','0','0','','education','admin',NOW(),'智能命题：须完成迁移并显式启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='exam');

-- 只采用名称、类型和路径均匹配的根目录；不匹配时编号为空，后续不插入子菜单。
SET @exam_root_id = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id=0 AND path='exam' AND menu_type='M' AND menu_name='智能命题');

-- 创建任务中心页面；组件路径对应前端任务中心，查询权限仅允许查看有权访问的任务。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '任务中心',@exam_root_id,1,'task','exam/task/index',1,1,'C','0','0','exam:task:list','job','admin',NOW(),'仅显示当前用户任务'
WHERE @exam_root_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@exam_root_id AND path='task');

-- 校验任务中心组件归属，防止把按钮挂到同路径的其他页面下。
SET @exam_task_menu_id = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id=@exam_root_id AND path='task' AND component='exam/task/index');

-- 插入缺失的任务按钮权限；创建与重试权限针对基础自检，不等于允许付费模型调用。
-- 按权限标识检查是否已存在，不覆盖已有权限记录，也不修改角色与菜单关联。
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

-- 两个编号都应非空；任一为 NULL 时停止并人工检查冲突，不重命名或删除无关菜单。
-- 功能启用后，由超级管理员显式分配父目录、页面及所需按钮权限。
SELECT @exam_root_id AS exam_root_id, @exam_task_menu_id AS exam_task_menu_id;
