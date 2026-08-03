package com.ruoyi.blog.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ruoyi.blog.constant.AiModuleCode;
import com.ruoyi.blog.domain.AiPromptTemplate;
import com.ruoyi.blog.domain.AiProvider;
import com.ruoyi.blog.dto.AiChatRequest;
import com.ruoyi.blog.service.AiPromptTemplateService;
import com.ruoyi.blog.service.AiProviderService;
import com.ruoyi.blog.service.AiTaskService;
import com.ruoyi.blog.service.DeepSeekService;
import com.ruoyi.blog.vo.AiPromptTemplateDetailVO;
import com.ruoyi.blog.vo.AiPromptTemplateVO;
import com.ruoyi.blog.vo.AiTaskVO;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/blog/ai")
@RequiredArgsConstructor
public class AiController extends BlogControllerSupport
{

    private final DeepSeekService deepSeekService;

    private final AiPromptTemplateService aiPromptTemplateService;

    private final AiTaskService aiTaskService;

    private final AiProviderService aiProviderService;

    @PreAuthorize("@ss.hasPermi('blog:ai:chat') or @ss.hasPermi('blog:ai:optimize') or @ss.hasPermi('blog:ai:write')")
    @GetMapping("/templates")
    public AjaxResult templates()
    {
        List<AiPromptTemplateVO> list = aiPromptTemplateService.listActive();
        return AjaxResult.success(list);
    }

    @PreAuthorize("@ss.hasPermi('blog:ai:chat') or @ss.hasPermi('blog:ai:optimize') or @ss.hasPermi('blog:ai:write')")
    @GetMapping("/templates/{scene}")
    public AjaxResult templateDetail(@PathVariable String scene)
    {
        AiPromptTemplate template = aiPromptTemplateService.getByScene(scene);
        AiPromptTemplateDetailVO vo = new AiPromptTemplateDetailVO();
        if (template != null)
        {
            BeanUtils.copyProperties(template, vo);
        }
        return AjaxResult.success(vo);
    }

    @PreAuthorize("@ss.hasPermi('blog:ai:write') or @ss.hasPermi('blog:ai:chat')")
    @GetMapping("/tasks/{id}")
    public AjaxResult task(@PathVariable Long id)
    {
        AiTaskVO vo = aiTaskService.getById(id);
        return AjaxResult.success(vo);
    }

    @PreAuthorize("@ss.hasPermi('blog:ai:chat')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        AiProvider provider = aiProviderService.resolveActiveProvider();
        boolean configured = provider != null;
        String model = configured ? provider.getDefaultModel() : "";
        String providerName = configured ? provider.getName() : "";
        String providerType = configured ? provider.getProviderType() : "";
        return AjaxResult.success(Map.of(
                "configured", configured,
                "model", model == null ? "" : model,
                "providerName", providerName == null ? "" : providerName,
                "providerType", providerType == null ? "" : providerType));
    }

    @PreAuthorize("@ss.hasPermi('blog:ai:chat')")
    @Log(title = "AI编辑器对话", businessType = BusinessType.AI, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody AiChatRequest request)
    {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onCompletion(() -> {
        });
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());
        deepSeekService.streamChat(request, emitter, AiModuleCode.EDITOR);
        return emitter;
    }
}
