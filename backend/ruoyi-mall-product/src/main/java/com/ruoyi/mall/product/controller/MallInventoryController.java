package com.ruoyi.mall.product.controller;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.mall.product.dto.MallInventoryAdjustRequest;
import com.ruoyi.mall.product.service.MallInventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/mall/inventory")
@RequiredArgsConstructor
public class MallInventoryController extends MallProductControllerSupport
{
    private final MallInventoryService mallInventoryService;

    @PreAuthorize("@ss.hasPermi('mall:spu:edit')")
    @Log(title = "商城库存调整", businessType = BusinessType.UPDATE)
    @PostMapping("/adjust")
    public AjaxResult adjust(@Valid @RequestBody MallInventoryAdjustRequest request)
    {
        return AjaxResult.success(mallInventoryService.adjust(request));
    }
}
