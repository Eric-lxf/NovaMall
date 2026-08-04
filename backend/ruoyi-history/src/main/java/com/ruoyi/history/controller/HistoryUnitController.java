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
import com.ruoyi.history.domain.HistoryLearningUnit;
import com.ruoyi.history.dto.HistoryUnitGenerateRequest;
import com.ruoyi.history.dto.HistoryUnitPageQuery;
import com.ruoyi.history.dto.HistoryUnitSaveRequest;
import com.ruoyi.history.service.HistoryUnitService;
import com.ruoyi.history.support.HistoryUnitContentCodec;
import com.ruoyi.history.vo.HistoryUnitVO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/history/units")
@RequiredArgsConstructor
public class HistoryUnitController extends HistoryControllerSupport
{
    private final HistoryUnitService historyUnitService;

    @PreAuthorize("@ss.hasPermi('history:unit:list')")
    @GetMapping
    public TableDataInfo page(@Valid HistoryUnitPageQuery query)
    {
        Page<HistoryLearningUnit> page = historyUnitService.page(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('history:unit:list') or @ss.hasPermi('history:path:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return AjaxResult.success(historyUnitService.listPublishedOptions());
    }

    @PreAuthorize("@ss.hasPermi('history:unit:query')")
    @GetMapping("/{id:\\d+}")
    public AjaxResult detail(@PathVariable Long id)
    {
        HistoryUnitVO vo = historyUnitService.getDetail(id);
        HistoryUnitSaveRequest form = new HistoryUnitSaveRequest();
        form.setId(vo.getId());
        form.setTitle(vo.getTitle());
        form.setEventId(vo.getEventId());
        form.setPeriodId(vo.getPeriodId());
        form.setOneLiner(vo.getOneLiner());
        form.setObjectives(vo.getObjectives());
        form.setPrerequisites(vo.getPrerequisites());
        form.setAuditStatus(vo.getAuditStatus());
        form.setStatus(vo.getStatus());
        form.setRemark(vo.getRemark());
        HistoryUnitContentCodec.applyToRequest(vo.getContent(), form);
        return AjaxResult.success(form);
    }

    @PreAuthorize("@ss.hasPermi('history:unit:add')")
    @Log(title = "历史学习单元", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody HistoryUnitSaveRequest request)
    {
        return AjaxResult.success(historyUnitService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('history:unit:edit')")
    @Log(title = "历史学习单元", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody HistoryUnitSaveRequest request)
    {
        historyUnitService.update(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:unit:remove')")
    @Log(title = "历史学习单元", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id:\\d+}")
    public AjaxResult remove(@PathVariable Long id)
    {
        historyUnitService.delete(id);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:unit:publish')")
    @Log(title = "历史学习单元发布", businessType = BusinessType.UPDATE)
    @PostMapping("/{id:\\d+}/publish")
    public AjaxResult publish(@PathVariable Long id)
    {
        historyUnitService.publish(id);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('history:unit:add')")
    @Log(title = "历史事件生成学习单元", businessType = BusinessType.INSERT)
    @PostMapping("/generate-from-event")
    public AjaxResult generateFromEvent(@Valid @RequestBody HistoryUnitGenerateRequest request)
    {
        return AjaxResult.success(historyUnitService.generateFromEvent(request));
    }
}
