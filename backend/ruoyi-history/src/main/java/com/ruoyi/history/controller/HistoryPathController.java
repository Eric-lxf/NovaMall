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
import com.ruoyi.history.domain.HistoryLearningPath;
import com.ruoyi.history.dto.HistoryPathPageQuery;
import com.ruoyi.history.dto.HistoryPathSaveRequest;
import com.ruoyi.history.service.HistoryPathService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/history/paths")
@RequiredArgsConstructor
public class HistoryPathController extends HistoryControllerSupport
{
    private final HistoryPathService historyPathService;

    @PreAuthorize("@ss.hasPermi('history:path:list')")
    @GetMapping
    public TableDataInfo page(@Valid HistoryPathPageQuery query)
    {
        Page<HistoryLearningPath> page = historyPathService.page(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:path:query')")
    @GetMapping("/{id:\\d+}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return AjaxResult.success(historyPathService.getDetail(id));
    }

    @PreAuthorize("@ss.hasPermi('history:path:add')")
    @Log(title = "历史学习路径", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody HistoryPathSaveRequest request)
    {
        return AjaxResult.success(historyPathService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('history:path:edit')")
    @Log(title = "历史学习路径", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody HistoryPathSaveRequest request)
    {
        historyPathService.update(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:path:remove')")
    @Log(title = "历史学习路径", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id:\\d+}")
    public AjaxResult remove(@PathVariable Long id)
    {
        historyPathService.delete(id);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:path:publish')")
    @Log(title = "历史学习路径发布", businessType = BusinessType.UPDATE)
    @PostMapping("/{id:\\d+}/publish")
    public AjaxResult publish(@PathVariable Long id)
    {
        historyPathService.publish(id);
        return AjaxResult.success();
    }
}
