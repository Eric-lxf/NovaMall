package com.ruoyi.history.controller;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.history.domain.HistoryEvent;
import com.ruoyi.history.dto.HistoryEventPageQuery;
import com.ruoyi.history.dto.HistoryEventSaveRequest;
import com.ruoyi.history.service.HistoryEventService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/history/events")
@RequiredArgsConstructor
public class HistoryEventController extends HistoryControllerSupport
{
    private final HistoryEventService historyEventService;

    @PreAuthorize("@ss.hasPermi('history:event:list')")
    @GetMapping
    public TableDataInfo page(@Valid HistoryEventPageQuery query)
    {
        Page<HistoryEvent> page = historyEventService.page(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:event:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return AjaxResult.success(historyEventService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('history:event:add')")
    @Log(title = "历史事件", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody HistoryEventSaveRequest request)
    {
        return AjaxResult.success(historyEventService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('history:event:edit')")
    @Log(title = "历史事件", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody HistoryEventSaveRequest request)
    {
        historyEventService.update(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:event:remove')")
    @Log(title = "历史事件", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        historyEventService.delete(id);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:event:publish')")
    @Log(title = "历史事件发布", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/publish")
    public AjaxResult publish(@PathVariable Long id)
    {
        historyEventService.publish(id);
        return AjaxResult.success();
    }
}
