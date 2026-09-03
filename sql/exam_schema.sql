-- P1 task foundation only. Does not create future source/question/paper tables.
-- Run manually against the intended nova_mall database after backup; no automatic production migration.
CREATE TABLE IF NOT EXISTS exam_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    title VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_no INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(64) NULL,
    lease_until DATETIME(3) NULL,
    error_code VARCHAR(64) NULL,
    result_summary VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_task_request (owner_user_id, kind, idempotency_key),
    KEY idx_exam_task_owner (owner_user_id, id),
    KEY idx_exam_task_queue (status, id),
    KEY idx_exam_task_lease (status, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能命题持久任务（P1）';
