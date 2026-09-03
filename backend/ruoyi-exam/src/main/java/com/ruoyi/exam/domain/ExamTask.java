package com.ruoyi.exam.domain;

import java.time.LocalDateTime;

/** Persistence snapshot. Never serialize this object directly to the browser. */
public record ExamTask(long id, long ownerUserId, String kind, String title, String idempotencyKey,
        String requestHash, String status, int attemptNo, long revision, String leaseOwner,
        LocalDateTime leaseUntil, String errorCode, String resultSummary,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
