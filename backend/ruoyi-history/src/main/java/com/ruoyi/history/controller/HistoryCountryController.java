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
import com.ruoyi.history.domain.HistoryCountry;
import com.ruoyi.history.dto.HistoryCountryPageQuery;
import com.ruoyi.history.dto.HistoryCountrySaveRequest;
import com.ruoyi.history.service.HistoryCountryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/history/countries")
@RequiredArgsConstructor
public class HistoryCountryController extends HistoryControllerSupport
{
    private final HistoryCountryService historyCountryService;

    @PreAuthorize("@ss.hasPermi('history:country:list')")
    @GetMapping
    public TableDataInfo page(@Valid HistoryCountryPageQuery query)
    {
        Page<HistoryCountry> page = historyCountryService.page(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:country:list') or @ss.hasPermi('history:period:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return AjaxResult.success(historyCountryService.listActive());
    }

    @PreAuthorize("@ss.hasPermi('history:country:query')")
    @GetMapping("/{id:\\d+}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return AjaxResult.success(historyCountryService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('history:country:add')")
    @Log(title = "历史国家", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody HistoryCountrySaveRequest request)
    {
        return AjaxResult.success(historyCountryService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('history:country:edit')")
    @Log(title = "历史国家", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody HistoryCountrySaveRequest request)
    {
        historyCountryService.update(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:country:remove')")
    @Log(title = "历史国家", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id:\\d+}")
    public AjaxResult remove(@PathVariable Long id)
    {
        historyCountryService.delete(id);
        return AjaxResult.success();
    }
}
