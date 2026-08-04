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
import com.ruoyi.history.domain.HistoryPerson;
import com.ruoyi.history.dto.HistoryPersonPageQuery;
import com.ruoyi.history.dto.HistoryPersonSaveRequest;
import com.ruoyi.history.service.HistoryPersonService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/history/person")
@RequiredArgsConstructor
public class HistoryPersonController extends HistoryControllerSupport
{
    private final HistoryPersonService historyPersonService;

    @PreAuthorize("@ss.hasPermi('history:person:list')")
    @GetMapping
    public TableDataInfo page(@Valid HistoryPersonPageQuery query)
    {
        Page<HistoryPerson> page = historyPersonService.page(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:person:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return AjaxResult.success(historyPersonService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('history:person:add')")
    @Log(title = "历史人物", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody HistoryPersonSaveRequest request)
    {
        return AjaxResult.success(historyPersonService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('history:person:edit')")
    @Log(title = "历史人物", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody HistoryPersonSaveRequest request)
    {
        historyPersonService.update(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:person:remove')")
    @Log(title = "历史人物", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        historyPersonService.delete(id);
        return AjaxResult.success();
    }
}
