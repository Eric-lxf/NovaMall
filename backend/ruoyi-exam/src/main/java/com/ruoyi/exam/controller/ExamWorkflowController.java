package com.ruoyi.exam.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.exam.service.*;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.support.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** No raw rows, storage keys, provider credentials or student answer fields cross this boundary. */
@RestController
@RequestMapping("/exam")
@ConditionalOnProperty(prefix="exam",name="enabled",havingValue="true")
public class ExamWorkflowController {
    private final ExamWorkflow workflow; private final ExamSources sources; private final ExamKnowledge knowledge;
    private final ExamBlueprints blueprints; private final ExamQuestions questions; private final ExamPapers papers;
    public ExamWorkflowController(ExamWorkflow workflow,ExamSources sources,ExamKnowledge knowledge,ExamBlueprints blueprints,ExamQuestions questions,ExamPapers papers) {
        this.workflow=workflow; this.sources=sources; this.knowledge=knowledge; this.blueprints=blueprints; this.questions=questions; this.papers=papers;
    }
    private ExamActor actor() { workflow.requireReady(); return ExamActor.current(); }
    private AjaxResult ok(Object value) { return AjaxResult.success(value); }

    @GetMapping("/workflow/capabilities") @PreAuthorize("@ss.hasPermi('exam:task:list')")
    public AjaxResult capabilities() { return ok(workflow.capabilities()); }
    @GetMapping("/sources") @PreAuthorize("@ss.hasPermi('exam:source:list')")
    public AjaxResult sources(@RequestParam(required=false) Long beforeId) { return ok(sources.list(actor(),beforeId)); }
    @GetMapping("/sources/{id}") @PreAuthorize("@ss.hasPermi('exam:source:list')")
    public AjaxResult source(@PathVariable long id) { return ok(sources.detail(actor(),id)); }
    @PostMapping("/sources/text") @PreAuthorize("@ss.hasPermi('exam:source:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult sourceText(@RequestBody JsonNode body) { return ok(sources.importText(actor(),body,null)); }
    @PostMapping("/sources/{id}/versions/text") @PreAuthorize("@ss.hasPermi('exam:source:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult sourceVersion(@PathVariable long id,@RequestBody JsonNode body) { return ok(sources.importText(actor(),body,id)); }
    @PostMapping("/sources/txt") @PreAuthorize("@ss.hasPermi('exam:source:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult sourceTxt(@RequestParam String title,@RequestParam MultipartFile file,
            @RequestParam(required=false) Long sourceId,@RequestParam(required=false) Long expectedRevision) throws IOException {
        var actor=actor(); ExamJson.require(file.getSize()>0 && file.getSize()<=10*1024*1024,"文件大小超出 10 MB");
        return ok(sources.importTxt(actor,title,file.getOriginalFilename(),file.getBytes(),sourceId,expectedRevision));
    }
    @GetMapping("/source-versions/{id}/fragments") @PreAuthorize("@ss.hasPermi('exam:source:list')")
    public AjaxResult fragments(@PathVariable long id) { return ok(sources.fragments(actor(),id)); }
    @PostMapping("/source-versions/{id}/confirm") @PreAuthorize("@ss.hasPermi('exam:source:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult sourceConfirm(@PathVariable long id,@RequestBody JsonNode body) { return ok(sources.confirm(actor(),id,body)); }
    @PostMapping("/sources/{id}/status") @PreAuthorize("@ss.hasPermi('exam:source:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult sourceStatus(@PathVariable long id,@RequestBody JsonNode body) { return ok(sources.toggle(actor(),id,body)); }
    @GetMapping("/source-versions/{id}/download") @PreAuthorize("@ss.hasPermi('exam:source:download')")
    public ResponseEntity<byte[]> sourceDownload(@PathVariable long id) { return attachment(sources.download(actor(),id)); }

    @GetMapping("/knowledge") @PreAuthorize("@ss.hasPermi('exam:knowledge:list')")
    public AjaxResult knowledge(@RequestParam(required=false) Long sourceVersionId,@RequestParam(required=false) Long beforeId) { return ok(knowledge.list(actor(),sourceVersionId,beforeId)); }
    @PostMapping("/knowledge") @PreAuthorize("@ss.hasPermi('exam:knowledge:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult knowledgeCreate(@RequestBody JsonNode body) { return ok(knowledge.save(actor(),null,body)); }
    @PutMapping("/knowledge/{id}") @PreAuthorize("@ss.hasPermi('exam:knowledge:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult knowledgeEdit(@PathVariable long id,@RequestBody JsonNode body) { return ok(knowledge.save(actor(),id,body)); }
    @PostMapping("/knowledge/{id}/confirm") @PreAuthorize("@ss.hasPermi('exam:knowledge:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult knowledgeConfirm(@PathVariable long id,@RequestBody JsonNode body) { return ok(knowledge.confirm(actor(),id,body)); }
    @PostMapping("/knowledge/{id}/status") @PreAuthorize("@ss.hasPermi('exam:knowledge:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult knowledgeStatus(@PathVariable long id,@RequestBody JsonNode body) { return ok(knowledge.toggle(actor(),id,body)); }
    @PostMapping("/knowledge/merge") @PreAuthorize("@ss.hasPermi('exam:knowledge:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult knowledgeMerge(@RequestBody JsonNode body) { return ok(knowledge.merge(actor(),body)); }

    @GetMapping("/blueprints") @PreAuthorize("@ss.hasPermi('exam:blueprint:list')")
    public AjaxResult blueprints(@RequestParam(required=false) Long beforeId) { return ok(blueprints.list(actor(),beforeId)); }
    @GetMapping("/blueprints/{id}") @PreAuthorize("@ss.hasPermi('exam:blueprint:list')")
    public AjaxResult blueprint(@PathVariable long id) { return ok(blueprints.detail(actor(),id)); }
    @PostMapping("/blueprints") @PreAuthorize("@ss.hasPermi('exam:blueprint:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult blueprintCreate(@RequestBody JsonNode body) { return ok(blueprints.save(actor(),null,body)); }
    @PutMapping("/blueprints/{id}") @PreAuthorize("@ss.hasPermi('exam:blueprint:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult blueprintEdit(@PathVariable long id,@RequestBody JsonNode body) { return ok(blueprints.save(actor(),id,body)); }
    @PostMapping("/blueprints/{id}/confirm") @PreAuthorize("@ss.hasPermi('exam:blueprint:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult blueprintConfirm(@PathVariable long id,@RequestBody JsonNode body) { return ok(blueprints.confirm(actor(),id,body)); }

    @GetMapping("/questions") @PreAuthorize("@ss.hasPermi('exam:question:list')")
    public AjaxResult questions(@RequestParam(required=false) Long beforeId) { return ok(questions.list(actor(),false,beforeId)); }
    @GetMapping("/questions/{id}") @PreAuthorize("@ss.hasPermi('exam:question:list') or @ss.hasPermi('exam:review:list')")
    public AjaxResult question(@PathVariable long id) { return ok(questions.detail(actor(),id)); }
    @PostMapping("/questions") @PreAuthorize("@ss.hasPermi('exam:question:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult questionCreate(@RequestBody JsonNode body) { return ok(questions.save(actor(),null,body)); }
    @PutMapping("/questions/{id}") @PreAuthorize("@ss.hasPermi('exam:question:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult questionEdit(@PathVariable long id,@RequestBody JsonNode body) { return ok(questions.save(actor(),id,body)); }
    @PostMapping("/questions/{id}/status") @PreAuthorize("@ss.hasPermi('exam:question:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult questionStatus(@PathVariable long id,@RequestBody JsonNode body) { return ok(questions.toggle(actor(),id,body)); }
    @PostMapping("/question-versions/{id}/submit") @PreAuthorize("@ss.hasPermi('exam:question:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult questionSubmit(@PathVariable long id,@RequestBody JsonNode body) { return ok(questions.submit(actor(),id,body)); }
    @GetMapping("/reviews") @PreAuthorize("@ss.hasPermi('exam:review:list')")
    public AjaxResult reviews(@RequestParam(required=false) Long beforeId) { return ok(questions.list(actor(),true,beforeId)); }
    @PostMapping("/question-versions/{id}/review") @PreAuthorize("@ss.hasPermi('exam:review:approve')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult review(@PathVariable long id,@RequestBody JsonNode body) { return ok(questions.review(actor(),id,body)); }

    @GetMapping("/papers") @PreAuthorize("@ss.hasPermi('exam:paper:list')")
    public AjaxResult papers(@RequestParam(required=false) Long beforeId) { return ok(papers.list(actor(),beforeId)); }
    @GetMapping("/papers/{id}") @PreAuthorize("@ss.hasPermi('exam:paper:list')")
    public AjaxResult paper(@PathVariable long id) { return ok(papers.detail(actor(),id)); }
    @PostMapping("/papers") @PreAuthorize("@ss.hasPermi('exam:paper:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult paperCreate(@RequestBody JsonNode body) { return ok(papers.save(actor(),null,body)); }
    @PutMapping("/papers/{id}") @PreAuthorize("@ss.hasPermi('exam:paper:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult paperEdit(@PathVariable long id,@RequestBody JsonNode body) { return ok(papers.save(actor(),id,body)); }
    @PostMapping("/papers/{id}/finalize") @PreAuthorize("@ss.hasPermi('exam:paper:edit')")
    @com.ruoyi.common.annotation.Log(title="智能命题业务变更", businessType=com.ruoyi.common.enums.BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult paperFinalize(@PathVariable long id,@RequestBody JsonNode body) { return ok(papers.finalizePaper(actor(),id,body)); }
    @GetMapping("/paper-versions/{id}/student") @PreAuthorize("@ss.hasPermi('exam:paper:list')")
    public AjaxResult student(@PathVariable long id) { return ok(papers.snapshot(actor(),id,true)); }
    @GetMapping("/paper-versions/{id}/teacher") @PreAuthorize("@ss.hasPermi('exam:paper:answers')")
    public AjaxResult teacher(@PathVariable long id) { return ok(papers.snapshot(actor(),id,false)); }
    public static ResponseEntity<byte[]> attachment(ExamFiles.Download file) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mime())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(file.name(),StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }
}
