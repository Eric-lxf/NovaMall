package com.ruoyi.exam.security;

@FunctionalInterface
public interface ExamExecutionAuthorizer {
    /** Resolve current database permissions again; do not reuse request-thread authentication. */
    boolean mayExecute(long userId);
    default boolean mayExecute(long userId,String permission) { return mayExecute(userId); }
}
