package com.ruoyi.mall.product.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.mall.product.dto.MallSpuPageQuery;
import com.ruoyi.mall.product.service.MallFrontCategoryService;
import com.ruoyi.mall.product.service.MallSpuService;
import com.ruoyi.mall.product.vo.MallPublicSpuVO;

import lombok.RequiredArgsConstructor;

@Anonymous
@RestController
@RequestMapping("/public/mall")
@RequiredArgsConstructor
public class PublicMallProductController extends MallProductControllerSupport
{
    private final MallSpuService mallSpuService;
    private final MallFrontCategoryService mallFrontCategoryService;

    @GetMapping("/spus")
    public TableDataInfo spus(@Valid MallSpuPageQuery query)
    {
        Page<MallPublicSpuVO> page = mallSpuService.publicPage(query);
        return mpPageTable(page);
    }

    @GetMapping("/spus/{id}")
    public AjaxResult spuDetail(@PathVariable Long id)
    {
        return AjaxResult.success(mallSpuService.publicDetail(id));
    }

    @GetMapping("/categories")
    public AjaxResult categories()
    {
        return AjaxResult.success(mallFrontCategoryService.listActiveTree());
    }
}
