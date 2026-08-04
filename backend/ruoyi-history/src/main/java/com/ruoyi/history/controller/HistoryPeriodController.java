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
import com.ruoyi.history.domain.HistoryPeriod;
import com.ruoyi.history.dto.HistoryPeriodPageQuery;
import com.ruoyi.history.dto.HistoryPeriodSaveRequest;
import com.ruoyi.history.service.HistoryPeriodService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/history/period")
@RequiredArgsConstructor
public class HistoryPeriodController extends HistoryControllerSupport
{
    private final HistoryPeriodService historyPeriodService;

    @PreAuthorize("@ss.hasPermi('history:period:list')")
    @GetMapping
    public TableDataInfo page(@Valid HistoryPeriodPageQuery query)
    {
        Page<HistoryPeriod> page = historyPeriodService.page(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:period:list') or @ss.hasPermi('history:event:list') or @ss.hasPermi('history:person:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return AjaxResult.success(historyPeriodService.listActive());
    }

    @PreAuthorize("@ss.hasPermi('history:period:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return AjaxResult.success(historyPeriodService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('history:period:add')")
    @Log(title = "历史时期", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody HistoryPeriodSaveRequest request)
    {
        return AjaxResult.success(historyPeriodService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('history:period:edit')")
    @Log(title = "历史时期", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody HistoryPeriodSaveRequest request)
    {
        historyPeriodService.update(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:period:remove')")
    @Log(title = "历史时期", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        historyPeriodService.delete(id);
        return AjaxResult.success();
    }
}
