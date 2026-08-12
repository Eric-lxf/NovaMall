package com.ruoyi.blog.external.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.blog.external.constant.BlogApiScopes;
import com.ruoyi.blog.external.dto.ExternalArticleCreateRequest;
import com.ruoyi.blog.external.dto.ExternalArticlePageQuery;
import com.ruoyi.blog.external.security.BlogApiPrincipal;
import com.ruoyi.blog.external.security.BlogApiRequestContext;
import com.ruoyi.blog.external.service.ExternalBlogArticleService;
import com.ruoyi.blog.external.vo.ExternalArticleCreateResult;
import com.ruoyi.blog.external.vo.ExternalArticleSummaryVO;
import com.ruoyi.blog.external.vo.ExternalArticleVO;
import com.ruoyi.blog.external.vo.ExternalPageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/open-api/v1/blog/articles", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExternalBlogArticleController
{
    private final ExternalBlogArticleService articleService;

    @PreAuthorize("hasAuthority('SCOPE_" + BlogApiScopes.ARTICLE_CREATE + "')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExternalArticleVO> createDraft(
            @AuthenticationPrincipal BlogApiPrincipal principal,
            @RequestHeader(value = BlogApiRequestContext.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ExternalArticleCreateRequest request,
            HttpServletRequest servletRequest)
    {
        ExternalArticleCreateResult result = articleService.createDraft(principal, idempotencyKey, request,
                servletRequest);
        URI location = URI.create("/open-api/v1/blog/articles/" + result.article().getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.article());
    }

    @PreAuthorize("hasAuthority('SCOPE_" + BlogApiScopes.ARTICLE_READ_OWN + "')")
    @GetMapping
    public ExternalPageResponse<ExternalArticleSummaryVO> page(
            @AuthenticationPrincipal BlogApiPrincipal principal,
            @Valid ExternalArticlePageQuery query)
    {
        return articleService.pageOwned(principal, query);
    }

    @PreAuthorize("hasAuthority('SCOPE_" + BlogApiScopes.ARTICLE_READ_OWN + "')")
    @GetMapping("/{id}")
    public ExternalArticleVO detail(@AuthenticationPrincipal BlogApiPrincipal principal,
            @PathVariable @Positive(message = "id must be positive") Long id, HttpServletRequest request)
    {
        ExternalArticleVO article = articleService.getOwned(principal.clientPk(), id);
        request.setAttribute(BlogApiRequestContext.ATTR_ARTICLE_ID, id);
        return article;
    }
}
