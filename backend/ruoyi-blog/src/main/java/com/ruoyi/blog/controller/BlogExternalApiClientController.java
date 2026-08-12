package com.ruoyi.blog.controller;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.dto.BlogApiClientCreateRequest;
import com.ruoyi.blog.dto.BlogApiClientPageQuery;
import com.ruoyi.blog.dto.BlogApiClientStatusRequest;
import com.ruoyi.blog.service.BlogApiClientAdminService;
import com.ruoyi.blog.vo.BlogApiClientSecretVO;
import com.ruoyi.blog.vo.BlogApiClientVO;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/blog/external-api/clients")
public class BlogExternalApiClientController extends BlogControllerSupport
{
    private final BlogApiClientAdminService blogApiClientAdminService;

    @PreAuthorize("@ss.hasPermi('blog:external-api:client:list')")
    @GetMapping
    public TableDataInfo page(@Valid BlogApiClientPageQuery query)
    {
        Page<BlogApiClientVO> page = blogApiClientAdminService.page(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('blog:external-api:client:add')")
    @Log(title = "外部API客户端", businessType = BusinessType.INSERT,
            isSaveResponseData = false,
            excludeParamNames = {"clientSecret", "clientSecretHash", "secret", "accessToken", "token"})
    @PostMapping
    public AjaxResult create(@Valid @RequestBody BlogApiClientCreateRequest request)
    {
        BlogApiClientSecretVO result = blogApiClientAdminService.create(request);
        return AjaxResult.success("客户端创建成功，请立即保存密钥，关闭后无法再次查看", result);
    }

    @PreAuthorize("@ss.hasPermi('blog:external-api:client:edit')")
    @Log(title = "外部API客户端状态", businessType = BusinessType.UPDATE,
            isSaveResponseData = false,
            excludeParamNames = {"clientSecret", "clientSecretHash", "secret", "accessToken", "token"})
    @PutMapping("/{id}/status")
    public AjaxResult updateStatus(@PathVariable Long id,
            @Valid @RequestBody BlogApiClientStatusRequest request)
    {
        blogApiClientAdminService.updateStatus(id, request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('blog:external-api:client:rotate')")
    @Log(title = "外部API客户端密钥轮换", businessType = BusinessType.UPDATE,
            isSaveResponseData = false,
            excludeParamNames = {"clientSecret", "clientSecretHash", "secret", "accessToken", "token"})
    @PostMapping("/{id}/rotate-secret")
    public AjaxResult rotateSecret(@PathVariable Long id)
    {
        BlogApiClientSecretVO result = blogApiClientAdminService.rotateSecret(id);
        return AjaxResult.success("密钥轮换成功，请立即保存新密钥，旧密钥已失效", result);
    }
}
