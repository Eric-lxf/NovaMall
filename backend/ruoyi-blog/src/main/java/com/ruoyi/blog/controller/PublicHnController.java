package com.ruoyi.blog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.service.BlogHnItemService;
import com.ruoyi.blog.vo.BlogHnItemVO;
import com.ruoyi.blog.vo.BlogHnListItemVO;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

@Anonymous
@RestController
@RequestMapping("/public/blog/hn")
@RequiredArgsConstructor
public class PublicHnController extends BlogControllerSupport
{

    private final BlogHnItemService blogHnItemService;

    @GetMapping("/boards/{board}/items")
    public TableDataInfo list(@PathVariable String board,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        Page<BlogHnListItemVO> page = blogHnItemService.publicBoardPage(board, pageNum, pageSize);
        return mpPageTable(page);
    }

    @GetMapping("/items/{hnId}")
    public AjaxResult detail(@PathVariable Long hnId)
    {
        BlogHnItemVO vo = blogHnItemService.getPublishedByHnId(hnId);
        return AjaxResult.success(vo);
    }
}
