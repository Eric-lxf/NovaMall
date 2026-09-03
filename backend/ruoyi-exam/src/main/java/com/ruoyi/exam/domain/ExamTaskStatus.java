package com.ruoyi.exam.domain;

public enum ExamTaskStatus {
    QUEUED, RUNNING, SUCCEEDED, PARTIAL_SUCCESS, FAILED, CANCEL_REQUESTED, CANCELLED, NEEDS_CONFIRMATION;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) return true;
        for (var status : values()) if (status.name().equals(value)) return true;
        return false;
    }
}
