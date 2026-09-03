package com.ruoyi.exam.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.exam.ai.ExamAiJobs;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/exam")
@ConditionalOnProperty(prefix="exam",name="enabled",havingValue="true")
public class ExamAiController {
    private final ExamAiJobs ai; private final ExamJobs jobs; private final ExamWorkflow workflow;
    public ExamAiController(ExamAiJobs ai,ExamJobs jobs,ExamWorkflow workflow) { this.ai=ai; this.jobs=jobs; this.workflow=workflow; }
    @GetMapping("/ai/capabilities") @PreAuthorize("@ss.hasPermi('exam:task:list')")
    public AjaxResult capabilities() { return AjaxResult.success(ai.capabilities()); }
    @PostMapping("/ai/preview") @PreAuthorize("@ss.hasPermi('exam:task:list')")
    public AjaxResult preview(@RequestBody JsonNode body) {
        workflow.requireReady();
        String kind=switch(body.path("operation").asText()) {
            case "extract" -> ExamAiJobs.KNOWLEDGE;
            case "generate" -> ExamAiJobs.GENERATE;
            case "verify" -> ExamAiJobs.VERIFY;
            default -> throw new com.ruoyi.exam.support.ExamException("EXAM_INPUT_INVALID","AI 操作无效");
        };
        return AjaxResult.success(ai.preview(ExamActor.current(),kind,body.path("payload")));
    }
    @GetMapping("/jobs/{id}") @PreAuthorize("@ss.hasPermi('exam:task:list')")
    public AjaxResult detail(@PathVariable long id) { workflow.requireReady(); return AjaxResult.success(jobs.detail(ExamActor.current(),id)); }
    @PostMapping("/ai/generate") @PreAuthorize("@ss.hasPermi('exam:question:generate')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult generate(@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) { return submit(ExamAiJobs.GENERATE,key,body); }
    @PostMapping("/ai/extract") @PreAuthorize("@ss.hasPermi('exam:knowledge:extract')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult extract(@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) { return submit(ExamAiJobs.KNOWLEDGE,key,body); }
    @PostMapping("/ai/verify") @PreAuthorize("@ss.hasPermi('exam:question:verify')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult verify(@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) { return submit(ExamAiJobs.VERIFY,key,body); }
    private AjaxResult submit(String kind,String key,JsonNode body) {
        workflow.requireReady(); return AjaxResult.success(ExamTaskController.TaskView.from(ai.submit(ExamActor.current(),kind,key,body)));
    }
}
