package com.ruoyi.exam.support;

public class ExamException extends RuntimeException {
    private final String errorCode;

    public ExamException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }

    public static ExamException notFound() {
        return new ExamException("EXAM_RESOURCE_NOT_FOUND", "资源不存在或无权访问");
    }

    public static ExamException conflict() {
        return new ExamException("EXAM_VERSION_CONFLICT", "任务状态已变化，请刷新后重试");
    }
}
