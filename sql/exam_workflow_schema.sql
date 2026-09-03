-- 智能命题：十九张业务、关联与计量表，执行顺序为四份命题脚本中的第 2 份。
-- 执行前备份并选中目标数据库；先执行 exam_schema.sql，再执行本脚本及两份菜单脚本。
-- 仅创建不存在的表，不覆盖数据；已有同名表不会自动更新字段、索引或中文注释。
-- 关联编号由应用校验，本脚本不创建外键；不得根据关联说明自行增加级联删除。
-- 结构化内容以 JSON 文本保存，由应用校验；时间字段统一使用北京时间（东八区）。

CREATE TABLE IF NOT EXISTS exam_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '私有文件主键',
    owner_user_id BIGINT NOT NULL COMMENT '文件所属用户编号',
    storage_key VARCHAR(80) NOT NULL COMMENT '私有存储相对键，不是公开下载地址',
    original_name VARCHAR(180) NOT NULL COMMENT '原始文件名或服务端生成的导出文件名',
    mime_type VARCHAR(100) NOT NULL COMMENT '文件媒体类型',
    size_bytes BIGINT NOT NULL COMMENT '文件大小，单位为字节',
    sha256 CHAR(64) NOT NULL COMMENT '文件内容摘要，用于完整性校验',
    purpose VARCHAR(24) NOT NULL COMMENT '文件用途，如待解析原件、资料原件或试卷导出',
    created_at DATETIME(3) NOT NULL COMMENT '文件记录创建时间，北京时间',
    -- 存储键不可重复；所属用户索引用于私有文件查询及配额核算。
    UNIQUE KEY uk_exam_file_key(storage_key),
    KEY idx_exam_file_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题私有文件';

CREATE TABLE IF NOT EXISTS exam_source (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '资料主键',
    owner_user_id BIGINT NOT NULL COMMENT '资料所属用户编号',
    title VARCHAR(160) NOT NULL COMMENT '资料标题',
    current_version_id BIGINT NULL COMMENT '当前资料版本编号，关联资料版本表',
    enabled INT NOT NULL DEFAULT 1 COMMENT '启用标志：1为启用，0为停用',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间，北京时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最近更新时间，北京时间',
    -- 支持按所属用户分页查询资料。
    KEY idx_exam_source_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题资料主表';

CREATE TABLE IF NOT EXISTS exam_source_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '资料版本主键',
    source_id BIGINT NOT NULL COMMENT '所属资料编号，关联资料主表',
    version_no INT NOT NULL COMMENT '同一资料内递增的版本序号',
    file_id BIGINT NOT NULL COMMENT '原始私有文件编号，关联私有文件表',
    status VARCHAR(32) NOT NULL COMMENT '确认状态：NEEDS_REVIEW为待确认，READY为已确认可用',
    content_hash CHAR(64) NOT NULL COMMENT '资料原始文件内容摘要',
    warnings_json LONGTEXT NOT NULL COMMENT '解析警告列表的结构化文本',
    external_allowed INT NOT NULL DEFAULT 0 COMMENT '外发模型授权：1为允许，0为禁止；与片段可用确认分开记录',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '片段确认与外发授权的乐观锁版本号',
    created_at DATETIME(3) NOT NULL COMMENT '版本创建时间，北京时间',
    -- 同一资料的版本序号不可重复。
    UNIQUE KEY uk_exam_source_version(source_id,version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题资料版本';

CREATE TABLE IF NOT EXISTS exam_source_fragment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '资料片段主键',
    source_version_id BIGINT NOT NULL COMMENT '所属资料版本编号',
    ordinal_no INT NOT NULL COMMENT '片段在资料版本内的顺序号，从1开始',
    content LONGTEXT NOT NULL COMMENT '片段原文，用于命题依据校验',
    locator_json LONGTEXT NOT NULL COMMENT '原文定位信息的结构化文本，如页码、段落或表格单元格',
    usable INT NOT NULL DEFAULT 0 COMMENT '人工确认可用标志：1为可用，0为不可用',
    -- 同一资料版本内片段顺序号不可重复。
    UNIQUE KEY uk_exam_fragment_order(source_version_id,ordinal_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题资料原文片段';

CREATE TABLE IF NOT EXISTS exam_knowledge_point (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '知识点主键',
    owner_user_id BIGINT NOT NULL COMMENT '知识点所属用户编号',
    source_version_id BIGINT NOT NULL COMMENT '知识点依据的资料版本编号',
    name VARCHAR(160) NOT NULL COMMENT '知识点名称',
    description VARCHAR(2000) NOT NULL COMMENT '知识点说明',
    refs_json LONGTEXT NOT NULL COMMENT '依据片段及原文引用列表的结构化文本',
    confirmed INT NOT NULL DEFAULT 0 COMMENT '人工确认标志：1为已确认，0为未确认',
    enabled INT NOT NULL DEFAULT 1 COMMENT '启用标志：1为启用，0为停用',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间，北京时间',
    -- 支持按所属用户与资料版本筛选知识点。
    KEY idx_exam_knowledge_owner(owner_user_id,source_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题知识点';

CREATE TABLE IF NOT EXISTS exam_blueprint (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '命题蓝图主键',
    owner_user_id BIGINT NOT NULL COMMENT '蓝图所属用户编号',
    title VARCHAR(160) NOT NULL COMMENT '蓝图标题',
    status VARCHAR(24) NOT NULL COMMENT '蓝图状态：DRAFT为草稿，CONFIRMED为已确认且不可原地修改',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    settings_json LONGTEXT NOT NULL COMMENT '命题要求与资料范围等蓝图配置的结构化文本',
    slots_json LONGTEXT NOT NULL COMMENT '题目槽位清单的结构化文本，记录题型、分值及知识点等要求',
    content_hash CHAR(64) NOT NULL COMMENT '蓝图内容摘要，用于校验确认后的命题要求',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间，北京时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最近更新时间，北京时间',
    -- 支持按所属用户分页查询蓝图。
    KEY idx_exam_blueprint_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题蓝图';

CREATE TABLE IF NOT EXISTS exam_question (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '题目主键',
    owner_user_id BIGINT NOT NULL COMMENT '题目所属用户编号',
    current_version_id BIGINT NULL COMMENT '当前题目版本编号，关联题目版本表',
    enabled INT NOT NULL DEFAULT 1 COMMENT '启用标志：1为启用，0为停用',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间，北京时间',
    -- 支持按所属用户分页查询题库。
    KEY idx_exam_question_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题题目主表';

CREATE TABLE IF NOT EXISTS exam_question_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '题目版本主键',
    question_id BIGINT NOT NULL COMMENT '所属题目编号，关联题目主表',
    version_no INT NOT NULL COMMENT '同一题目内递增的版本序号',
    blueprint_id BIGINT NOT NULL COMMENT '所属命题蓝图编号',
    slot_id VARCHAR(64) NOT NULL COMMENT '对应蓝图中的题目槽位标识',
    content_json LONGTEXT NOT NULL COMMENT '题干、选项、答案、解析、评分点及引用等题目内容的结构化文本',
    content_hash CHAR(64) NOT NULL COMMENT '本版本题目内容摘要，用于绑定复核和审核结果',
    review_state VARCHAR(24) NOT NULL COMMENT '审核状态：DRAFT为草稿，PENDING_REVIEW为待审核，APPROVED为已批准，REJECTED为已退回',
    origin VARCHAR(24) NOT NULL COMMENT '题目内容来源，如人工录入或模型生成',
    created_at DATETIME(3) NOT NULL COMMENT '版本创建时间，北京时间',
    -- 版本序号防重；蓝图及槽位索引用于查找对应候选题目版本。
    UNIQUE KEY uk_exam_question_version(question_id,version_no),
    KEY idx_exam_question_blueprint(blueprint_id,slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题题目版本';

CREATE TABLE IF NOT EXISTS exam_question_source (
    question_version_id BIGINT NOT NULL COMMENT '题目版本编号',
    source_version_id BIGINT NOT NULL COMMENT '引用的资料版本编号',
    fragment_id BIGINT NOT NULL COMMENT '引用的原文片段编号',
    quote_text VARCHAR(1000) NOT NULL COMMENT '引用原文，须匹配对应资料片段',
    quote_hash CHAR(64) NOT NULL COMMENT '引用原文摘要，用于同一片段内引用去重',
    -- 复合主键防止重复引用；资料版本索引用于追踪受来源变更影响的题目。
    PRIMARY KEY(question_version_id,fragment_id,quote_hash),
    KEY idx_exam_question_source(source_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题题目依据关联';

CREATE TABLE IF NOT EXISTS exam_question_check (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '题目校验记录主键',
    question_version_id BIGINT NOT NULL COMMENT '被校验的题目版本编号',
    content_hash CHAR(64) NOT NULL COMMENT '校验时的题目内容摘要，防止结果用于已修改内容',
    check_kind VARCHAR(24) NOT NULL COMMENT '校验类型，如规则校验或模型独立复核',
    result_json LONGTEXT NOT NULL COMMENT '校验结果及问题明细的结构化文本',
    passed INT NOT NULL COMMENT '校验通过标志：1为通过，0为未通过',
    created_at DATETIME(3) NOT NULL COMMENT '校验记录创建时间，北京时间',
    -- 支持按题目版本查询最新校验记录。
    KEY idx_exam_check_version(question_version_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题题目规则校验与独立复核记录';

CREATE TABLE IF NOT EXISTS exam_question_review (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '人工审核记录主键',
    question_version_id BIGINT NOT NULL COMMENT '被审核的题目版本编号',
    content_hash CHAR(64) NOT NULL COMMENT '审核时的题目内容摘要',
    reviewer_id BIGINT NOT NULL COMMENT '审核人用户编号',
    decision VARCHAR(24) NOT NULL COMMENT '审核决定：APPROVED为批准，REJECTED为退回',
    reason VARCHAR(2000) NOT NULL COMMENT '人工审核意见或退回原因',
    created_at DATETIME(3) NOT NULL COMMENT '审核时间，北京时间',
    -- 支持按题目版本追溯人工审核记录。
    KEY idx_exam_review_version(question_version_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题人工审核记录';

CREATE TABLE IF NOT EXISTS exam_paper (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '试卷主键',
    owner_user_id BIGINT NOT NULL COMMENT '试卷所属用户编号',
    title VARCHAR(160) NOT NULL COMMENT '试卷标题',
    status VARCHAR(24) NOT NULL COMMENT '试卷状态：DRAFT为草稿，FINALIZED为已定版',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    draft_json LONGTEXT NOT NULL COMMENT '试卷草稿配置及选题清单的结构化文本',
    current_version_id BIGINT NULL COMMENT '当前定版试卷版本编号，未定版时为空',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间，北京时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最近更新时间，北京时间',
    -- 支持按所属用户分页查询试卷。
    KEY idx_exam_paper_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题试卷主表';

CREATE TABLE IF NOT EXISTS exam_paper_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '定版试卷版本主键',
    paper_id BIGINT NOT NULL COMMENT '所属试卷编号',
    version_no INT NOT NULL COMMENT '同一试卷内的定版版本序号',
    snapshot_json LONGTEXT NOT NULL COMMENT '定版时完整试卷快照，含教师答案；学生输出须单独过滤',
    content_hash CHAR(64) NOT NULL COMMENT '定版试卷快照的内容摘要',
    created_at DATETIME(3) NOT NULL COMMENT '定版时间，北京时间',
    -- 同一试卷的定版版本序号不可重复。
    UNIQUE KEY uk_exam_paper_version(paper_id,version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题定版试卷快照';

CREATE TABLE IF NOT EXISTS exam_paper_item (
    paper_version_id BIGINT NOT NULL COMMENT '所属定版试卷版本编号',
    ordinal_no INT NOT NULL COMMENT '题目在试卷中的顺序号，从1开始',
    question_version_id BIGINT NOT NULL COMMENT '定版时选用的已审核题目版本编号',
    score DECIMAL(8,2) NOT NULL COMMENT '本题在试卷中的分值',
    -- 同一试卷版本题序唯一；题目版本索引用于追踪引用该题的试卷。
    PRIMARY KEY(paper_version_id,ordinal_no),
    KEY idx_exam_paper_question(question_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题定版试卷题目关联';

CREATE TABLE IF NOT EXISTS exam_job (
    task_id BIGINT NOT NULL PRIMARY KEY COMMENT '业务任务编号，与持久任务主键一一对应',
    input_json LONGTEXT NOT NULL COMMENT '任务执行所需的私有输入快照，不保存模型密钥',
    result_json LONGTEXT NOT NULL COMMENT '业务任务结果的结构化文本',
    progress_json LONGTEXT NOT NULL COMMENT '业务任务进度的结构化文本',
    calls_reserved INT NOT NULL DEFAULT 0 COMMENT '累计预占的模型调用次数，不代表全部成功',
    tokens_reserved BIGINT NOT NULL DEFAULT 0 COMMENT '累计预占的模型词元预算，不等于实际用量'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题业务任务输入与预算';

CREATE TABLE IF NOT EXISTS exam_task_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '任务分项主键',
    task_id BIGINT NOT NULL COMMENT '所属持久任务编号',
    slot_id VARCHAR(64) NOT NULL COMMENT '分项标识，如题目槽位或抽取批次标识',
    status VARCHAR(24) NOT NULL COMMENT '分项状态：QUEUED为待处理，SUCCEEDED为成功，FAILED为失败',
    result_version_id BIGINT NULL COMMENT '成功生成或复核的题目版本编号，其他分项可为空',
    error_code VARCHAR(64) NULL COMMENT '分项失败的业务错误码',
    -- 同一任务内分项标识不可重复。
    UNIQUE KEY uk_exam_task_slot(task_id,slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题任务分项执行结果';

CREATE TABLE IF NOT EXISTS exam_ai_call (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '模型调用记录主键',
    task_id BIGINT NOT NULL COMMENT '所属持久任务编号',
    call_no INT NOT NULL COMMENT '任务内递增的模型调用序号',
    provider_id BIGINT NOT NULL COMMENT '模型服务商配置编号，关联现有模型配置',
    model_name VARCHAR(160) NOT NULL COMMENT '本次调用使用的模型名称',
    status VARCHAR(32) NOT NULL COMMENT '模型调用状态，区分调用中、已响应、失败或结果不确定',
    usage_json LONGTEXT NOT NULL COMMENT '服务商返回的实际用量信息，不保存完整请求或响应正文',
    request_id VARCHAR(200) NULL COMMENT '服务商返回的请求标识，用于调用追踪',
    finish_reason VARCHAR(80) NULL COMMENT '服务商返回的生成结束原因',
    duration_ms BIGINT NULL COMMENT '本次模型调用耗时，单位为毫秒',
    error_code VARCHAR(64) NULL COMMENT '模型调用错误码，不保存密钥或原始异常正文',
    created_at DATETIME(3) NOT NULL COMMENT '调用记录创建时间，北京时间',
    -- 同一任务内调用序号不可重复。
    UNIQUE KEY uk_exam_ai_call(task_id,call_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题模型调用计量与追踪';

CREATE TABLE IF NOT EXISTS exam_export (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '试卷导出记录主键',
    owner_user_id BIGINT NOT NULL COMMENT '导出记录所属用户编号',
    paper_version_id BIGINT NOT NULL COMMENT '导出的定版试卷版本编号',
    format VARCHAR(12) NOT NULL COMMENT '导出格式：DOCX为文字文档，PDF为便携文档，XLSX为电子表格',
    audience VARCHAR(12) NOT NULL COMMENT '内容受众：STUDENT为无答案学生版，TEACHER为含答案教师版',
    task_id BIGINT NOT NULL COMMENT '对应的持久导出任务编号',
    file_id BIGINT NULL COMMENT '导出成功后的私有文件编号，完成前为空',
    status VARCHAR(24) NOT NULL COMMENT '导出产物状态；完整任务状态与下载许可须同时核对持久任务',
    created_at DATETIME(3) NOT NULL COMMENT '导出记录创建时间，北京时间',
    -- 每个导出任务仅对应一条记录；所属用户索引用于查询导出列表。
    UNIQUE KEY uk_exam_export_task(task_id),
    KEY idx_exam_export_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题试卷私有导出记录';

-- 仅在短事务内锁定记录，用于串行化任务准入、配额等并发检查；不持锁执行模型调用。
CREATE TABLE IF NOT EXISTS exam_owner_lock (
    owner_user_id BIGINT NOT NULL PRIMARY KEY COMMENT '锁定对象编号，用户编号用于用户级锁，0为模块级共享锁'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能命题并发准入与配额锁';
