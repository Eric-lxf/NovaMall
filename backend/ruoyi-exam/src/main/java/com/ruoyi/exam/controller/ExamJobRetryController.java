package com.ruoyi.exam.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam/jobs")
@ConditionalOnProperty(prefix="exam",name="enabled",havingValue="true")
public class ExamJobRetryController {
    private final ExamJobRetries retries;
    private final ExamWorkflow workflow;
    public ExamJobRetryController(ExamJobRetries retries,ExamWorkflow workflow) { this.retries=retries; this.workflow=workflow; }
    @GetMapping("/{id}/retry-preview")
    @PreAuthorize("@ss.hasPermi('exam:task:retry')")
    public AjaxResult preview(@PathVariable long id) { workflow.requireReady(); return AjaxResult.success(retries.preview(ExamActor.current(),id)); }
    @PostMapping("/{id}/retry")
    @PreAuthorize("@ss.hasPermi('exam:task:retry')")
    @Log(title="手动重试命题业务任务",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult retry(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) {
        workflow.requireReady(); return AjaxResult.success(ExamTaskController.TaskView.from(retries.retry(ExamActor.current(),id,key,body)));
    }
}
