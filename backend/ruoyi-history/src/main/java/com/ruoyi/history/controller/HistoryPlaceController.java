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
import com.ruoyi.history.domain.HistoryPlace;
import com.ruoyi.history.dto.HistoryPlacePageQuery;
import com.ruoyi.history.dto.HistoryPlaceSaveRequest;
import com.ruoyi.history.service.HistoryPlaceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/history/place")
@RequiredArgsConstructor
public class HistoryPlaceController extends HistoryControllerSupport
{
    private final HistoryPlaceService historyPlaceService;

    @PreAuthorize("@ss.hasPermi('history:place:list')")
    @GetMapping
    public TableDataInfo page(@Valid HistoryPlacePageQuery query)
    {
        Page<HistoryPlace> page = historyPlaceService.page(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:place:list') or @ss.hasPermi('history:event:list') or @ss.hasPermi('history:person:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return AjaxResult.success(historyPlaceService.listActive());
    }

    @PreAuthorize("@ss.hasPermi('history:place:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return AjaxResult.success(historyPlaceService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('history:place:add')")
    @Log(title = "历史地点", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody HistoryPlaceSaveRequest request)
    {
        return AjaxResult.success(historyPlaceService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('history:place:edit')")
    @Log(title = "历史地点", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody HistoryPlaceSaveRequest request)
    {
        historyPlaceService.update(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:place:remove')")
    @Log(title = "历史地点", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        historyPlaceService.delete(id);
        return AjaxResult.success();
    }
}
