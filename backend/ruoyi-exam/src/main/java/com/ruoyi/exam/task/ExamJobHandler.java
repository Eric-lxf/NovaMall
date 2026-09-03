package com.ruoyi.exam.task;

import com.ruoyi.exam.domain.ExamTask;

public interface ExamJobHandler {
    boolean supports(String kind);
    Outcome execute(ExamTask task,int attempt,String worker);
    record Outcome(String status,String error,String summary) { }
}
