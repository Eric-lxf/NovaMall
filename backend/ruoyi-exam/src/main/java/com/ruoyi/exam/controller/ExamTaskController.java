package com.ruoyi.exam.controller;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.domain.ExamTask;
import com.ruoyi.exam.domain.ExamTaskStatus;
import com.ruoyi.exam.repository.ExamTaskRepository;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.service.ExamReadiness;
import com.ruoyi.exam.service.ExamTaskService;
import com.ruoyi.exam.support.ExamException;

@RestController
@RequestMapping("/exam")
public class ExamTaskController {
    private final ExamProperties properties;
    private final ObjectProvider<ExamTaskService> services;
    private final ObjectProvider<ExamTaskRepository> repositories;
    private final ObjectProvider<ExamReadiness> readiness;

    public ExamTaskController(ExamProperties properties, ObjectProvider<ExamTaskService> services,
            ObjectProvider<ExamTaskRepository> repositories, ObjectProvider<ExamReadiness> readiness) {
        this.properties = properties; this.services = services; this.repositories = repositories; this.readiness = readiness;
    }

    @GetMapping("/capabilities")
    @PreAuthorize("@ss.hasPermi('exam:task:list')")
    public AjaxResult capabilities() {
        boolean ready = properties.isEnabled() && readiness.getIfAvailable() != null && readiness.getObject().isReady();
        return AjaxResult.success(Map.of("enabled", properties.isEnabled(), "taskReady", ready,
                "workerEnabled", properties.isEnabled() && properties.isWorkerEnabled(),
                "aiEnabled", properties.isEnabled() && properties.isAiEnabled(),
                "uploadEnabled", ready && properties.getPrivateRoot()!=null && !properties.getPrivateRoot().isBlank(), "phase", "WORKFLOW_MVP"));
    }

    @GetMapping("/tasks")
    @PreAuthorize("@ss.hasPermi('exam:task:list')")
    public TableDataInfo page(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) String status) {
        active();
        if (pageNum < 1 || pageNum > 100000 || pageSize < 1 || pageSize > 100 || !ExamTaskStatus.isValid(status)) {
            throw new ExamException("EXAM_INPUT_INVALID", "分页或任务状态无效");
        }
        String filter = status == null || status.isBlank() ? null : status;
        // The initial list is always "my tasks", including for administrators. No client-supplied owner filter.
        long owner = ExamActor.current().userId();
        var repository = repositories.getObject();
        var result = new TableDataInfo(repository.page(owner, filter, (pageNum - 1) * pageSize, pageSize).stream()
                .map(TaskView::from).toList(), repository.count(owner, filter));
        result.setCode(200);
        result.setMsg("查询成功");
        return result;
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("@ss.hasPermi('exam:task:list')")
    public AjaxResult detail(@PathVariable long id) { return AjaxResult.success(TaskView.from(active().get(ExamActor.current(), id))); }

    @PostMapping("/tasks/check")
    @PreAuthorize("@ss.hasPermi('exam:task:create')")
    @Log(title = "命题基础自检", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult create(@RequestHeader("Idempotency-Key") String key, @RequestBody CheckRequest request) {
        return AjaxResult.success(TaskView.from(active().createCheck(ExamActor.current(), request.title(), key)));
    }

    @PostMapping("/tasks/{id}/cancel")
    @PreAuthorize("@ss.hasPermi('exam:task:cancel')")
    @Log(title = "取消命题任务", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult cancel(@PathVariable long id, @Valid @RequestBody RevisionRequest request) {
        return AjaxResult.success(TaskView.from(active().cancel(ExamActor.current(), id, request.expectedRevision())));
    }

    @PostMapping("/tasks/{id}/retry")
    @PreAuthorize("@ss.hasPermi('exam:task:retry')")
    @Log(title = "重试命题基础自检", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult retry(@PathVariable long id, @Valid @RequestBody RevisionRequest request) {
        return AjaxResult.success(TaskView.from(active().retry(ExamActor.current(), id, request.expectedRevision())));
    }

    private ExamTaskService active() {
        if (!properties.isEnabled()) throw new ExamException("EXAM_DISABLED", "智能命题尚未启用");
        readiness.getObject().requireReady();
        return services.getObject();
    }

    public record CheckRequest(String title) { }
    public record RevisionRequest(@NotNull @PositiveOrZero Long expectedRevision) { }
    public record TaskView(String id, String kind, String title, String status, int attemptNo, long revision,
            String errorCode, String resultSummary, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {
        static TaskView from(ExamTask task) {
            return new TaskView(Long.toString(task.id()), task.kind(), task.title(), task.status(), task.attemptNo(),
                    task.revision(), task.errorCode(), task.resultSummary(), task.createdAt(), task.updatedAt());
        }
    }
}
