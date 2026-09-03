package com.ruoyi.exam.controller;

import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.exam.document.ExamDocumentJobs;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.service.ExamWorkflow;
import com.ruoyi.exam.support.ExamJson;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/exam")
@ConditionalOnProperty(prefix="exam",name="enabled",havingValue="true")
public class ExamDocumentController {
    private final ExamDocumentJobs documents; private final ExamWorkflow workflow;
    public ExamDocumentController(ExamDocumentJobs documents,ExamWorkflow workflow) { this.documents=documents; this.workflow=workflow; }
    @GetMapping("/documents/capabilities") @PreAuthorize("@ss.hasPermi('exam:task:list')")
    public AjaxResult capabilities() { return AjaxResult.success(ExamJson.object().put("configured",documents.configured())); }
    @PostMapping("/sources/document") @PreAuthorize("@ss.hasPermi('exam:source:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult upload(@RequestHeader("Idempotency-Key") String key,@RequestParam String title,@RequestParam MultipartFile file,
            @RequestParam(required=false) Long sourceId,@RequestParam(required=false) Long expectedRevision) throws IOException {
        workflow.requireReady(); ExamJson.require(file.getSize()>0 && file.getSize()<=10*1024*1024,"文件大小超出 10 MB");
        return AjaxResult.success(ExamTaskController.TaskView.from(documents.parse(ExamActor.current(),key,title,file.getOriginalFilename(),file.getBytes(),sourceId,expectedRevision)));
    }
    @PostMapping("/exports") @PreAuthorize("@ss.hasPermi('exam:paper:export') and (#body.path('audience').asText() != 'TEACHER' or @ss.hasPermi('exam:paper:answers'))")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult export(@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) {
        workflow.requireReady(); return AjaxResult.success(ExamTaskController.TaskView.from(documents.export(ExamActor.current(),key,body)));
    }
    @GetMapping("/exports") @PreAuthorize("@ss.hasPermi('exam:paper:export')")
    public AjaxResult exports(@RequestParam(required=false) Long beforeId) { workflow.requireReady(); return AjaxResult.success(documents.exports(ExamActor.current(),beforeId)); }
    @GetMapping("/exports/{id}/download") @PreAuthorize("@ss.hasPermi('exam:paper:export')")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        workflow.requireReady(); return ExamWorkflowController.attachment(documents.download(ExamActor.current(),id));
    }
}
