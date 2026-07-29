SET NAMES utf8mb4;
USE nova_mall;

-- 每 30 分钟同步 HN 四榜并增量翻译（status=0 正常启用）
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT 'HN 四榜同步', 'BLOG', 'blogHnTask.syncAll()', '0 0/30 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 30 分钟同步 HN 四榜并增量翻译'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'blogHnTask.syncAll()');
