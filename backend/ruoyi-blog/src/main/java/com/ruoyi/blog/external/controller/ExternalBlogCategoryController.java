package com.ruoyi.blog.external.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.blog.external.constant.BlogApiScopes;
import com.ruoyi.blog.external.service.ExternalBlogArticleService;
import com.ruoyi.blog.external.vo.ExternalCategoryVO;
import com.ruoyi.blog.external.vo.ExternalTagVO;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/open-api/v1/blog", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExternalBlogCategoryController
{
    private final ExternalBlogArticleService articleService;

    @PreAuthorize("hasAuthority('SCOPE_" + BlogApiScopes.TAXONOMY_READ + "')")
    @GetMapping("/categories")
    public List<ExternalCategoryVO> categories()
    {
        return articleService.listCategories();
    }

    @PreAuthorize("hasAuthority('SCOPE_" + BlogApiScopes.TAXONOMY_READ + "')")
    @GetMapping("/tags")
    public List<ExternalTagVO> tags()
    {
        return articleService.listTags();
    }
}
