-- 智能命题：持久任务基础表，执行顺序为四份命题脚本中的第 1 份。
-- 执行前备份并明确选中目标 nova_mall 数据库；本脚本不自动切换数据库、不启用功能。
-- 业务时间统一按北京时间（东八区）保存；资料、题目、试卷等业务表由下一份脚本创建。
-- 仅创建不存在的表：已有同名表不会自动补齐字段、索引或本次新增的中文注释。
-- 任务状态：QUEUED=排队，RUNNING=执行中，SUCCEEDED=成功，PARTIAL_SUCCESS=部分成功，
-- FAILED=失败，CANCEL_REQUESTED=已请求取消，CANCELLED=已取消，NEEDS_CONFIRMATION=需要人工确认。
CREATE TABLE IF NOT EXISTS exam_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务主键',
    owner_user_id BIGINT NOT NULL COMMENT '任务所属用户编号，关联系统用户',
    kind VARCHAR(32) NOT NULL COMMENT '任务类型，如基础自检、资料解析、知识点抽取、题目生成、独立复核或试卷导出',
    title VARCHAR(80) NOT NULL COMMENT '任务展示标题，不保存完整资料或题目正文',
    idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求幂等键，同一用户和任务类型下防止重复提交',
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求内容摘要，用于识别幂等键对应的请求是否发生变化',
    status VARCHAR(32) NOT NULL COMMENT '任务状态，状态取值及中文含义见脚本顶部说明',
    attempt_no INT NOT NULL DEFAULT 0 COMMENT '执行尝试序号，配合租约隔离过期执行者',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发状态修改相互覆盖',
    lease_owner VARCHAR(64) NULL COMMENT '当前持有执行租约的工作进程标识',
    lease_until DATETIME(3) NULL COMMENT '执行租约到期时间，精确到毫秒',
    error_code VARCHAR(64) NULL COMMENT '可公开的业务错误码，不保存原始异常正文',
    result_summary VARCHAR(1000) NULL COMMENT '可公开的执行结果摘要，不保存私有正文或模型密钥',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间，北京时间，精确到毫秒',
    updated_at DATETIME(3) NOT NULL COMMENT '最近更新时间，北京时间，精确到毫秒',
    PRIMARY KEY (id),
    -- 同一用户、任务类型与幂等键仅允许对应一个任务。
    UNIQUE KEY uk_exam_task_request (owner_user_id, kind, idempotency_key),
    -- 支持按所属用户分页查询任务。
    KEY idx_exam_task_owner (owner_user_id, id),
    -- 支持按状态和任务编号领取排队任务。
    KEY idx_exam_task_queue (status, id),
    -- 支持查找租约过期的执行中任务。
    KEY idx_exam_task_lease (status, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能命题持久任务';
