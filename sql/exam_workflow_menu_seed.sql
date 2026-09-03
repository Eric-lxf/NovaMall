-- Additive menu seed; run serially AFTER exam_menu_seed.sql. Does not grant roles or overwrite menus.
START TRANSACTION;
SET @exam_root_id = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id=0 AND path='exam' AND menu_type='M' AND menu_name='智能命题');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '命题工作台',@exam_root_id,0,'workbench','exam/workbench/index',1,1,'C','0','0','exam:task:list','education','admin',NOW(),'资料、知识点、蓝图、审核、试卷与导出'
WHERE @exam_root_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@exam_root_id AND path='workbench');
SET @exam_workbench_id = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id=@exam_root_id AND path='workbench' AND component='exam/workbench/index');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT desired.label,@exam_workbench_id,desired.sort_order,'#','',1,1,'F','0','0',desired.permission,'#','admin',NOW(),'命题业务权限：需管理员显式分配'
FROM (
 SELECT '资料查询' label,1 sort_order,'exam:source:list' permission
 UNION ALL SELECT '资料编辑与授权',2,'exam:source:edit'
 UNION ALL SELECT '原文件下载',3,'exam:source:download'
 UNION ALL SELECT '知识点查询',4,'exam:knowledge:list'
 UNION ALL SELECT '知识点编辑确认',5,'exam:knowledge:edit'
 UNION ALL SELECT 'AI 知识点抽取',6,'exam:knowledge:extract'
 UNION ALL SELECT '蓝图查询',7,'exam:blueprint:list'
 UNION ALL SELECT '蓝图编辑确认',8,'exam:blueprint:edit'
 UNION ALL SELECT '题库查询',9,'exam:question:list'
 UNION ALL SELECT '题目编辑与提交',10,'exam:question:edit'
 UNION ALL SELECT 'AI 出题',11,'exam:question:generate'
 UNION ALL SELECT 'AI 独立复核',12,'exam:question:verify'
 UNION ALL SELECT '人工审核队列',13,'exam:review:list'
 UNION ALL SELECT '批准或退回题目',14,'exam:review:approve'
 UNION ALL SELECT '试卷查询',15,'exam:paper:list'
 UNION ALL SELECT '试卷编排与定版',16,'exam:paper:edit'
 UNION ALL SELECT '教师答案查看',17,'exam:paper:answers'
 UNION ALL SELECT '试卷导出下载',18,'exam:paper:export'
) desired
WHERE @exam_workbench_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms=desired.permission);
COMMIT;
SELECT @exam_root_id AS exam_root_id,@exam_workbench_id AS exam_workbench_id;
