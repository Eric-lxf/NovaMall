-- Additive MVP workflow tables. Run after exam_schema.sql in an explicitly selected database.
CREATE TABLE IF NOT EXISTS exam_file (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL,
 storage_key VARCHAR(80) NOT NULL, original_name VARCHAR(180) NOT NULL, mime_type VARCHAR(100) NOT NULL,
 size_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL, purpose VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_exam_file_key(storage_key), KEY idx_exam_file_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_source (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL, title VARCHAR(160) NOT NULL,
 current_version_id BIGINT NULL, enabled INT NOT NULL DEFAULT 1, revision BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, KEY idx_exam_source_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_source_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, source_id BIGINT NOT NULL, version_no INT NOT NULL,
 file_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL, content_hash CHAR(64) NOT NULL,
 warnings_json LONGTEXT NOT NULL, external_allowed INT NOT NULL DEFAULT 0, revision BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, UNIQUE KEY uk_exam_source_version(source_id,version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_source_fragment (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, source_version_id BIGINT NOT NULL, ordinal_no INT NOT NULL,
 content LONGTEXT NOT NULL, locator_json LONGTEXT NOT NULL, usable INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_exam_fragment_order(source_version_id,ordinal_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_knowledge_point (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL, source_version_id BIGINT NOT NULL,
 name VARCHAR(160) NOT NULL, description VARCHAR(2000) NOT NULL, refs_json LONGTEXT NOT NULL,
 confirmed INT NOT NULL DEFAULT 0, enabled INT NOT NULL DEFAULT 1, revision BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, KEY idx_exam_knowledge_owner(owner_user_id,source_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_blueprint (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL, title VARCHAR(160) NOT NULL,
 status VARCHAR(24) NOT NULL, revision BIGINT NOT NULL DEFAULT 0, settings_json LONGTEXT NOT NULL,
 slots_json LONGTEXT NOT NULL, content_hash CHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 KEY idx_exam_blueprint_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_question (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL, current_version_id BIGINT NULL,
 enabled INT NOT NULL DEFAULT 1, revision BIGINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL,
 KEY idx_exam_question_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_question_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, question_id BIGINT NOT NULL, version_no INT NOT NULL,
 blueprint_id BIGINT NOT NULL, slot_id VARCHAR(64) NOT NULL, content_json LONGTEXT NOT NULL, content_hash CHAR(64) NOT NULL,
 review_state VARCHAR(24) NOT NULL, origin VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_exam_question_version(question_id,version_no), KEY idx_exam_question_blueprint(blueprint_id,slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_question_source (
 question_version_id BIGINT NOT NULL, source_version_id BIGINT NOT NULL, fragment_id BIGINT NOT NULL,
 quote_text VARCHAR(1000) NOT NULL, quote_hash CHAR(64) NOT NULL,
 PRIMARY KEY(question_version_id,fragment_id,quote_hash), KEY idx_exam_question_source(source_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_question_check (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, question_version_id BIGINT NOT NULL, content_hash CHAR(64) NOT NULL,
 check_kind VARCHAR(24) NOT NULL, result_json LONGTEXT NOT NULL, passed INT NOT NULL, created_at DATETIME(3) NOT NULL,
 KEY idx_exam_check_version(question_version_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_question_review (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, question_version_id BIGINT NOT NULL, content_hash CHAR(64) NOT NULL,
 reviewer_id BIGINT NOT NULL, decision VARCHAR(24) NOT NULL, reason VARCHAR(2000) NOT NULL, created_at DATETIME(3) NOT NULL,
 KEY idx_exam_review_version(question_version_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_paper (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL, title VARCHAR(160) NOT NULL,
 status VARCHAR(24) NOT NULL, revision BIGINT NOT NULL DEFAULT 0, draft_json LONGTEXT NOT NULL,
 current_version_id BIGINT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 KEY idx_exam_paper_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_paper_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, paper_id BIGINT NOT NULL, version_no INT NOT NULL,
 snapshot_json LONGTEXT NOT NULL, content_hash CHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_exam_paper_version(paper_id,version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_paper_item (
 paper_version_id BIGINT NOT NULL, ordinal_no INT NOT NULL, question_version_id BIGINT NOT NULL, score DECIMAL(8,2) NOT NULL,
 PRIMARY KEY(paper_version_id,ordinal_no), KEY idx_exam_paper_question(question_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_job (
 task_id BIGINT NOT NULL PRIMARY KEY, input_json LONGTEXT NOT NULL, result_json LONGTEXT NOT NULL,
 progress_json LONGTEXT NOT NULL, calls_reserved INT NOT NULL DEFAULT 0, tokens_reserved BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_task_item (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, task_id BIGINT NOT NULL, slot_id VARCHAR(64) NOT NULL,
 status VARCHAR(24) NOT NULL, result_version_id BIGINT NULL, error_code VARCHAR(64) NULL,
 UNIQUE KEY uk_exam_task_slot(task_id,slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_ai_call (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, task_id BIGINT NOT NULL, call_no INT NOT NULL,
 provider_id BIGINT NOT NULL, model_name VARCHAR(160) NOT NULL, status VARCHAR(32) NOT NULL,
 usage_json LONGTEXT NOT NULL, request_id VARCHAR(200) NULL, finish_reason VARCHAR(80) NULL,
 duration_ms BIGINT NULL, error_code VARCHAR(64) NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_exam_ai_call(task_id,call_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_export (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL, paper_version_id BIGINT NOT NULL,
 format VARCHAR(12) NOT NULL, audience VARCHAR(12) NOT NULL, task_id BIGINT NOT NULL,
 file_id BIGINT NULL, status VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_exam_export_task(task_id), KEY idx_exam_export_owner(owner_user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_owner_lock (owner_user_id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
