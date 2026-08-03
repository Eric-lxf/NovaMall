package com.ruoyi.blog.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.blog.dto.AiWriteWizardRequest;
import com.ruoyi.blog.service.AiWriteService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/blog/ai/write")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermi('blog:ai:write')")
public class AiWriteController extends BlogControllerSupport
{

    private final AiWriteService aiWriteService;

    @Log(title = "AI智写-标题", businessType = BusinessType.AI, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/titles")
    public AjaxResult titles(@Valid @RequestBody AiWriteWizardRequest request)
    {
        Long taskId = aiWriteService.submitGenerateTitles(request);
        return AjaxResult.success(Map.of("taskId", taskId, "async", true));
    }

    @Log(title = "AI智写-摘要", businessType = BusinessType.AI, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/summary")
    public AjaxResult summary(@Valid @RequestBody AiWriteWizardRequest request)
    {
        Long taskId = aiWriteService.submitGenerateSummary(request);
        return AjaxResult.success(Map.of("taskId", taskId, "async", true));
    }

    @Log(title = "AI智写-大纲", businessType = BusinessType.AI, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/outline")
    public AjaxResult outline(@Valid @RequestBody AiWriteWizardRequest request)
    {
        Long taskId = aiWriteService.submitGenerateOutline(request);
        return AjaxResult.success(Map.of("taskId", taskId, "async", true));
    }

    @Log(title = "AI智写-全文", businessType = BusinessType.AI, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/generate")
    public AjaxResult generate(@Valid @RequestBody AiWriteWizardRequest request)
    {
        Long taskId = aiWriteService.submitGenerateArticle(request);
        return AjaxResult.success(Map.of("taskId", taskId, "async", true));
    }
}
