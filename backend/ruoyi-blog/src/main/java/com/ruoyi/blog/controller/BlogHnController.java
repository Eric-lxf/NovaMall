package com.ruoyi.blog.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.constant.HnBoard;
import com.ruoyi.blog.dto.HnItemAdminQuery;
import com.ruoyi.blog.dto.HnSyncRequest;
import com.ruoyi.blog.service.BlogHnItemService;
import com.ruoyi.blog.service.hn.HnSyncService;
import com.ruoyi.blog.vo.BlogHnItemVO;
import com.ruoyi.blog.vo.BlogHnListItemVO;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/blog/hn")
@RequiredArgsConstructor
public class BlogHnController extends BlogControllerSupport
{

    private final BlogHnItemService blogHnItemService;
    private final HnSyncService hnSyncService;

    @PreAuthorize("@ss.hasPermi('blog:hn:list')")
    @GetMapping("/items")
    public TableDataInfo page(@Valid HnItemAdminQuery query)
    {
        Page<BlogHnListItemVO> page = blogHnItemService.adminPage(query);
        return mpPageTable(page);
    }

    @PreAuthorize("@ss.hasPermi('blog:hn:query')")
    @GetMapping("/items/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        BlogHnItemVO vo = blogHnItemService.getById(id);
        return AjaxResult.success(vo);
    }

    @PreAuthorize("@ss.hasPermi('blog:hn:list')")
    @GetMapping("/sync/status")
    public AjaxResult syncStatus()
    {
        return AjaxResult.success(hnSyncService.syncStatus());
    }

    @PreAuthorize("@ss.hasPermi('blog:hn:sync')")
    @Log(title = "HN 同步", businessType = BusinessType.OTHER)
    @PostMapping("/sync/{board}")
    public AjaxResult syncBoard(@PathVariable String board, @RequestBody(required = false) HnSyncRequest request)
    {
        HnBoard.fromCode(board);
        boolean started = hnSyncService.syncBoardAsync(board);
        return AjaxResult.success(Map.of("started", started));
    }

    @PreAuthorize("@ss.hasPermi('blog:hn:sync')")
    @Log(title = "HN 同步", businessType = BusinessType.OTHER)
    @PostMapping("/sync")
    public AjaxResult syncAll(@RequestBody(required = false) HnSyncRequest request)
    {
        boolean started = false;
        for (HnBoard board : HnBoard.values())
        {
            if (hnSyncService.syncBoardAsync(board.getCode()))
            {
                started = true;
            }
        }
        return AjaxResult.success(Map.of("started", started));
    }
}
