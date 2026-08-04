SET NAMES utf8mb4;
USE nova_mall;

-- AI 历史学习平台：独立 history_ 表前缀，不复用 blog_/mall_ 模型

CREATE TABLE IF NOT EXISTS `history_country` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '国家/文明ID',
  `name`         varchar(64)  NOT NULL COMMENT '名称，如中国、埃及、美国',
  `alias`        varchar(128) DEFAULT NULL COMMENT '别名',
  `region`       varchar(64)  DEFAULT NULL COMMENT '区域，如东亚/北非/北美',
  `period_label` varchar(32)  NOT NULL DEFAULT '时期' COMMENT '时期称呼：朝代/王朝/时期',
  `summary`      varchar(1000) DEFAULT NULL COMMENT '简介',
  `sort`         int          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`       char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`    varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`       varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_history_country_name` (`name`),
  KEY `idx_history_country_region` (`region`),
  KEY `idx_history_country_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史国家/文明';

CREATE TABLE IF NOT EXISTS `history_period` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '时期ID',
  `name`               varchar(64)  NOT NULL COMMENT '时期名称，如秦汉',
  `alias`              varchar(128) DEFAULT NULL COMMENT '别名',
  `country_id`         bigint       DEFAULT NULL COMMENT '所属国家/文明',
  `start_year`         int          DEFAULT NULL COMMENT '起始年（负数为公元前）',
  `end_year`           int          DEFAULT NULL COMMENT '结束年',
  `date_precision`     varchar(32)  NOT NULL DEFAULT 'YEAR' COMMENT 'YEAR/CENTURY/APPROXIMATE/PERIOD',
  `original_date_text` varchar(128) DEFAULT NULL COMMENT '原始纪年文本',
  `calendar_type`      varchar(64)  DEFAULT NULL COMMENT '历法类型',
  `is_approximate`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否约数',
  `summary`            varchar(1000) DEFAULT NULL COMMENT '简介',
  `sort`               int          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`             char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`          varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`        datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`             varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_history_period_year` (`start_year`, `end_year`),
  KEY `idx_history_period_status` (`status`),
  KEY `idx_history_period_country` (`country_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史时期';

CREATE TABLE IF NOT EXISTS `history_place` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '地点ID',
  `name`               varchar(128) NOT NULL COMMENT '地点名称',
  `alias`              varchar(255) DEFAULT NULL COMMENT '别名',
  `modern_name`        varchar(128) DEFAULT NULL COMMENT '今地名',
  `region`             varchar(128) DEFAULT NULL COMMENT '区域',
  `longitude`          decimal(10,6) DEFAULT NULL COMMENT '经度',
  `latitude`           decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `summary`            varchar(1000) DEFAULT NULL COMMENT '简介',
  `audit_status`       varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED',
  `status`             char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`          varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`        datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`             varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_history_place_name` (`name`),
  KEY `idx_history_place_audit` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史地点';

CREATE TABLE IF NOT EXISTS `history_person` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '人物ID',
  `name`               varchar(64)  NOT NULL COMMENT '姓名',
  `alias`              varchar(255) DEFAULT NULL COMMENT '别名/字号',
  `period_id`          bigint       DEFAULT NULL COMMENT '所属时期',
  `birth_year`         int          DEFAULT NULL COMMENT '生年（负数为公元前）',
  `death_year`         int          DEFAULT NULL COMMENT '卒年',
  `date_precision`     varchar(32)  NOT NULL DEFAULT 'YEAR' COMMENT '时间精度',
  `original_date_text` varchar(128) DEFAULT NULL COMMENT '原始纪年文本',
  `calendar_type`      varchar(64)  DEFAULT NULL COMMENT '历法类型',
  `is_approximate`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否约数',
  `birth_place_id`     bigint       DEFAULT NULL COMMENT '出生地',
  `summary`            varchar(2000) DEFAULT NULL COMMENT '简介',
  `uncertainty_note`   varchar(1000) DEFAULT NULL COMMENT '不确定性说明',
  `audit_status`       varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
  `status`             char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`          varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`        datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`             varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_history_person_name` (`name`),
  KEY `idx_history_person_period` (`period_id`),
  KEY `idx_history_person_year` (`birth_year`, `death_year`),
  KEY `idx_history_person_audit` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史人物';

CREATE TABLE IF NOT EXISTS `history_event` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `title`              varchar(200) NOT NULL COMMENT '事件标题',
  `period_id`          bigint       DEFAULT NULL COMMENT '所属时期',
  `place_id`           bigint       DEFAULT NULL COMMENT '主要地点',
  `start_year`         int          DEFAULT NULL COMMENT '开始年（负数为公元前）',
  `end_year`           int          DEFAULT NULL COMMENT '结束年',
  `date_precision`     varchar(32)  NOT NULL DEFAULT 'YEAR' COMMENT '时间精度',
  `original_date_text` varchar(128) DEFAULT NULL COMMENT '原始纪年文本',
  `calendar_type`      varchar(64)  DEFAULT NULL COMMENT '历法类型',
  `is_approximate`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否约数',
  `summary`            varchar(2000) DEFAULT NULL COMMENT '一句话/摘要',
  `background`         text         COMMENT '背景',
  `process`            text         COMMENT '过程',
  `cause_analysis`     text         COMMENT '原因分析',
  `impact`             text         COMMENT '结果与影响',
  `uncertainty_note`   varchar(1000) DEFAULT NULL COMMENT '不确定性说明',
  `audit_status`       varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
  `status`             char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`          varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`        datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`             varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_history_event_period` (`period_id`),
  KEY `idx_history_event_place` (`place_id`),
  KEY `idx_history_event_year` (`start_year`, `end_year`),
  KEY `idx_history_event_audit` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史事件';

CREATE TABLE IF NOT EXISTS `history_concept` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '概念/制度ID',
  `name`         varchar(128) NOT NULL COMMENT '名称',
  `type`         varchar(32)  NOT NULL DEFAULT 'CONCEPT' COMMENT 'CONCEPT/INSTITUTION',
  `period_id`    bigint       DEFAULT NULL COMMENT '主要所属时期',
  `summary`      varchar(2000) DEFAULT NULL COMMENT '简介',
  `audit_status` varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
  `status`       char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`    varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`       varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_history_concept_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史概念与制度';

CREATE TABLE IF NOT EXISTS `history_source_document` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '资料文档ID',
  `title`         varchar(255) NOT NULL COMMENT '标题',
  `file_type`     varchar(32)  NOT NULL COMMENT 'PDF/TEXT/MARKDOWN',
  `file_url`      varchar(500) DEFAULT NULL COMMENT '存储路径或URL',
  `file_name`     varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `file_size`     bigint       DEFAULT NULL COMMENT '字节数',
  `content_text`  longtext     COMMENT '纯文本内容（TXT/MD 或解析结果）',
  `source_desc`   varchar(1000) DEFAULT NULL COMMENT '来源说明',
  `parse_status`  varchar(32)  NOT NULL DEFAULT 'QUEUED' COMMENT '解析状态',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_history_source_doc_status` (`parse_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史资料文档';

CREATE TABLE IF NOT EXISTS `history_source_fragment` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '资料片段ID',
  `document_id`   bigint       NOT NULL COMMENT '所属文档',
  `seq_no`        int          NOT NULL DEFAULT 0 COMMENT '片段序号',
  `page_no`       int          DEFAULT NULL COMMENT '页码',
  `locator`       varchar(255) DEFAULT NULL COMMENT '定位信息',
  `content`       text         NOT NULL COMMENT '片段正文',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_history_fragment_doc` (`document_id`, `seq_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史资料文本片段';

CREATE TABLE IF NOT EXISTS `history_knowledge_claim` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '知识主张ID',
  `claim_type`    varchar(32)  NOT NULL COMMENT 'EVENT/PERSON/PLACE/RELATION/FACT',
  `target_type`   varchar(32)  DEFAULT NULL COMMENT '关联对象类型',
  `target_id`     bigint       DEFAULT NULL COMMENT '关联对象ID',
  `fragment_id`   bigint       DEFAULT NULL COMMENT '来源片段',
  `document_id`   bigint       DEFAULT NULL COMMENT '来源文档',
  `claim_text`    varchar(2000) NOT NULL COMMENT '主张内容',
  `confidence`    decimal(5,2) DEFAULT NULL COMMENT '置信度',
  `uncertainty_note` varchar(1000) DEFAULT NULL COMMENT '不确定性',
  `audit_status`  varchar(32)  NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '审核状态',
  `task_id`       bigint       DEFAULT NULL COMMENT 'AI任务ID',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_history_claim_target` (`target_type`, `target_id`),
  KEY `idx_history_claim_fragment` (`fragment_id`),
  KEY `idx_history_claim_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基于来源的知识主张';

CREATE TABLE IF NOT EXISTS `history_event_relation` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `from_event_id` bigint       NOT NULL COMMENT '源事件',
  `to_event_id`   bigint       NOT NULL COMMENT '目标事件',
  `relation_type` varchar(32)  NOT NULL COMMENT 'CAUSES/LEADS_TO/PART_OF/HAPPENS_BEFORE/INFLUENCES/CONFLICTS_WITH/SIMILAR_TO',
  `description`   varchar(1000) DEFAULT NULL COMMENT '说明',
  `audit_status`  varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_history_rel_from` (`from_event_id`),
  KEY `idx_history_rel_to` (`to_event_id`),
  KEY `idx_history_rel_type` (`relation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件关系';

CREATE TABLE IF NOT EXISTS `history_learning_unit` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '学习单元ID',
  `title`         varchar(200) NOT NULL COMMENT '标题',
  `event_id`      bigint       DEFAULT NULL COMMENT '关联事件',
  `period_id`     bigint       DEFAULT NULL COMMENT '所属时期',
  `one_liner`     varchar(500) DEFAULT NULL COMMENT '一句话概括',
  `objectives`    text         COMMENT '学习目标JSON/文本',
  `prerequisites` text         COMMENT '前置知识',
  `content_json`  longtext     COMMENT '结构化正文',
  `audit_status`  varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
  `status`        char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_history_unit_event` (`event_id`),
  KEY `idx_history_unit_period` (`period_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习单元';

CREATE TABLE IF NOT EXISTS `history_learning_path` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '学习路径ID',
  `title`         varchar(200) NOT NULL COMMENT '标题',
  `summary`       varchar(1000) DEFAULT NULL COMMENT '简介',
  `period_id`     bigint       DEFAULT NULL COMMENT '主时期',
  `difficulty`    varchar(32)  DEFAULT 'BEGINNER' COMMENT '难度',
  `estimated_days` int         DEFAULT NULL COMMENT '预计天数',
  `audit_status`  varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
  `status`        char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习路径';

CREATE TABLE IF NOT EXISTS `history_learning_path_unit` (
  `id`        bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `path_id`   bigint NOT NULL COMMENT '路径ID',
  `unit_id`   bigint NOT NULL COMMENT '单元ID',
  `sort`      int    NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_history_path_unit` (`path_id`, `unit_id`),
  KEY `idx_history_path_unit_unit` (`unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习路径单元关联';

CREATE TABLE IF NOT EXISTS `history_question` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `unit_id`       bigint       DEFAULT NULL COMMENT '学习单元',
  `event_id`      bigint       DEFAULT NULL COMMENT '关联事件',
  `question_type` varchar(32)  NOT NULL COMMENT 'SINGLE/MULTI/ORDER/MATCH',
  `stem`          varchar(2000) NOT NULL COMMENT '题干',
  `explanation`   text         COMMENT '解析',
  `difficulty`    varchar(32)  DEFAULT 'NORMAL' COMMENT '难度',
  `audit_status`  varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
  `status`        char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_history_question_unit` (`unit_id`),
  KEY `idx_history_question_type` (`question_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库';

CREATE TABLE IF NOT EXISTS `history_question_option` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '选项ID',
  `question_id` bigint       NOT NULL COMMENT '题目ID',
  `option_key`  varchar(16)  NOT NULL COMMENT '选项键 A/B/C 或序号',
  `content`     varchar(1000) NOT NULL COMMENT '选项内容',
  `is_correct`  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否正确',
  `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_history_option_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目选项';

CREATE TABLE IF NOT EXISTS `history_quiz_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '答题记录ID',
  `user_id`       bigint       NOT NULL COMMENT '用户ID',
  `question_id`   bigint       NOT NULL COMMENT '题目ID',
  `user_answer`   varchar(2000) DEFAULT NULL COMMENT '用户作答',
  `is_correct`    tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否正确',
  `spent_seconds` int          DEFAULT NULL COMMENT '用时秒',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  PRIMARY KEY (`id`),
  KEY `idx_history_quiz_user` (`user_id`, `create_time`),
  KEY `idx_history_quiz_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录';

CREATE TABLE IF NOT EXISTS `history_learning_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '学习记录ID',
  `user_id`       bigint       NOT NULL COMMENT '用户ID',
  `path_id`       bigint       DEFAULT NULL COMMENT '路径ID',
  `unit_id`       bigint       NOT NULL COMMENT '单元ID',
  `progress`      int          NOT NULL DEFAULT 0 COMMENT '进度0-100',
  `completed`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否完成',
  `last_study_time` datetime   DEFAULT NULL COMMENT '最近学习时间',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_history_learn_user_unit` (`user_id`, `unit_id`),
  KEY `idx_history_learn_path` (`path_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习进度';

CREATE TABLE IF NOT EXISTS `history_review_schedule` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '复习计划ID',
  `user_id`       bigint       NOT NULL COMMENT '用户ID',
  `question_id`   bigint       NOT NULL COMMENT '题目ID',
  `wrong_count`   int          NOT NULL DEFAULT 0 COMMENT '累计错误次数',
  `interval_days` int          NOT NULL DEFAULT 1 COMMENT '下次间隔天数',
  `next_review_at` datetime    NOT NULL COMMENT '下次复习时间',
  `last_result`   char(1)      DEFAULT NULL COMMENT '上次对错 0错1对',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_history_review_user_q` (`user_id`, `question_id`),
  KEY `idx_history_review_next` (`user_id`, `next_review_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='间隔复习计划';

CREATE TABLE IF NOT EXISTS `history_ai_task` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_type`       varchar(64)  NOT NULL COMMENT 'IMPORT_PARSE/EXTRACT/GENERATE_UNIT/GENERATE_QUIZ',
  `document_id`     bigint       DEFAULT NULL COMMENT '关联资料',
  `status`          varchar(32)  NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED/PROCESSING/PENDING_REVIEW/SUCCEEDED/FAILED/RETRYING',
  `provider_code`   varchar(64)  DEFAULT NULL COMMENT '模型提供方',
  `model_name`      varchar(128) DEFAULT NULL COMMENT '模型名',
  `input_payload`   longtext     COMMENT '输入快照',
  `output_payload`  longtext     COMMENT '原始输出',
  `error_message`   varchar(2000) DEFAULT NULL COMMENT '错误信息',
  `retry_count`     int          NOT NULL DEFAULT 0 COMMENT '重试次数',
  `started_at`      datetime     DEFAULT NULL COMMENT '开始时间',
  `finished_at`     datetime     DEFAULT NULL COMMENT '结束时间',
  `create_by`       varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_history_ai_task_status` (`status`),
  KEY `idx_history_ai_task_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史 AI 异步任务';
