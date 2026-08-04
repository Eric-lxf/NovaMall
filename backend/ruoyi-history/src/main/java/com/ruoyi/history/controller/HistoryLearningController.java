package com.ruoyi.history.controller;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryAiTask;
import com.ruoyi.history.domain.HistorySourceDocument;
import com.ruoyi.history.dto.HistoryAiTaskPageQuery;
import com.ruoyi.history.dto.HistoryClaimAuditRequest;
import com.ruoyi.history.dto.HistoryClaimPageQuery;
import com.ruoyi.history.dto.HistoryExtractRequest;
import com.ruoyi.history.dto.HistorySourceImportRequest;
import com.ruoyi.history.dto.HistorySourcePageQuery;
import com.ruoyi.history.dto.HistoryTimelineQuery;
import com.ruoyi.history.service.HistoryEventService;
import com.ruoyi.history.service.HistoryExtractService;
import com.ruoyi.history.service.HistorySourceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryLearningController extends HistoryControllerSupport
{
    private final HistoryEventService historyEventService;
    private final HistorySourceService historySourceService;
    private final HistoryExtractService historyExtractService;

    /** C 端时间线：仅已发布事件 */
    @Anonymous
    @GetMapping("/timeline")
    public AjaxResult timeline(@Valid HistoryTimelineQuery query)
    {
        return AjaxResult.success(historyEventService.timeline(query));
    }

    @Anonymous
    @GetMapping("/events/{id}/public")
    public AjaxResult publicEvent(@PathVariable Long id)
    {
        var event = historyEventService.getById(id);
        if (!HistoryConstants.STATUS_NORMAL.equals(event.getStatus())
                || !HistoryConstants.AUDIT_PUBLISHED.equals(event.getAuditStatus()))
        {
            return AjaxResult.error("历史事件不存在或未发布");
        }
        return AjaxResult.success(event);
    }

    @PreAuthorize("@ss.hasPermi('history:source:import')")
    @Log(title = "历史资料导入", businessType = BusinessType.INSERT)
    @PostMapping("/source/import")
    public AjaxResult importSource(@Valid @RequestBody HistorySourceImportRequest request)
    {
        return AjaxResult.success(historySourceService.importSource(request));
    }

    @PreAuthorize("@ss.hasPermi('history:source:list')")
    @GetMapping("/source")
    public TableDataInfo sourcePage(@Valid HistorySourcePageQuery query)
    {
        Page<HistorySourceDocument> page = historySourceService.pageDocuments(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:source:query')")
    @GetMapping("/source/{id}")
    public AjaxResult sourceDetail(@PathVariable Long id,
            @RequestParam(value = "includeFragments", defaultValue = "false") boolean includeFragments)
    {
        return AjaxResult.success(historySourceService.getDocumentDetail(id, includeFragments));
    }

    @PreAuthorize("@ss.hasPermi('history:source:query')")
    @GetMapping("/source/{id}/fragments")
    public AjaxResult sourceFragments(@PathVariable Long id)
    {
        return AjaxResult.success(historySourceService.listFragments(id));
    }

    @PreAuthorize("@ss.hasPermi('history:source:query')")
    @GetMapping("/fragments/{fragmentId}")
    public AjaxResult fragmentDetail(@PathVariable Long fragmentId)
    {
        return AjaxResult.success(historySourceService.getFragment(fragmentId));
    }

    @PreAuthorize("@ss.hasPermi('history:task:list')")
    @GetMapping("/tasks")
    public TableDataInfo taskPage(@Valid HistoryAiTaskPageQuery query)
    {
        Page<HistoryAiTask> page = historySourceService.pageTasks(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:task:query')")
    @GetMapping("/tasks/{taskId}")
    public AjaxResult taskDetail(@PathVariable Long taskId)
    {
        return AjaxResult.success(historySourceService.getTask(taskId));
    }

    @PreAuthorize("@ss.hasPermi('history:task:retry')")
    @Log(title = "历史任务重试", businessType = BusinessType.UPDATE)
    @PostMapping("/tasks/{taskId}/retry")
    public AjaxResult retryTask(@PathVariable Long taskId)
    {
        HistoryAiTask task = historySourceService.getTask(taskId);
        if (HistoryConstants.TASK_TYPE_EXTRACT.equals(task.getTaskType()))
        {
            return AjaxResult.success(historyExtractService.retryExtract(taskId));
        }
        return AjaxResult.success(historySourceService.retryTask(taskId));
    }

    @PreAuthorize("@ss.hasPermi('history:source:extract')")
    @Log(title = "历史资料AI抽取", businessType = BusinessType.INSERT)
    @PostMapping("/source/extract")
    public AjaxResult extract(@Valid @RequestBody HistoryExtractRequest request)
    {
        return AjaxResult.success(historyExtractService.submitExtract(request));
    }

    @PreAuthorize("@ss.hasPermi('history:claim:list')")
    @GetMapping("/claims")
    public TableDataInfo claimPage(@Valid HistoryClaimPageQuery query)
    {
        return mpPageTable(historyExtractService.pageClaims(query));
    }

    @PreAuthorize("@ss.hasPermi('history:claim:audit')")
    @Log(title = "历史知识主张审核", businessType = BusinessType.UPDATE)
    @PostMapping("/claims/audit")
    public AjaxResult auditClaims(@Valid @RequestBody HistoryClaimAuditRequest request)
    {
        historyExtractService.auditClaims(request);
        return AjaxResult.success();
    }
}
