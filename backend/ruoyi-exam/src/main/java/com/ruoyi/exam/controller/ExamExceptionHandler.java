package com.ruoyi.exam.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.exam.support.ExamException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ruoyi.exam.controller")
public class ExamExceptionHandler {
    @ExceptionHandler(ExamException.class)
    public AjaxResult handle(ExamException exception) {
        return AjaxResult.error(exception.getMessage()).put("errorCode", exception.getErrorCode());
    }

    @ExceptionHandler(DataAccessException.class)
    public AjaxResult databaseUnavailable(DataAccessException exception) {
        return AjaxResult.error("命题任务数据暂不可用，请稍后重试").put("errorCode", "EXAM_NOT_READY");
    }
}
