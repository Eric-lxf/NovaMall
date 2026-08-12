package com.ruoyi.blog.external.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.blog.domain.BlogArticle;
import com.ruoyi.blog.domain.BlogCategory;
import com.ruoyi.blog.domain.BlogTag;
import com.ruoyi.blog.dto.ArticleTagRow;
import com.ruoyi.blog.external.config.BlogExternalApiProperties;
import com.ruoyi.blog.external.domain.BlogApiIdempotency;
import com.ruoyi.blog.external.dto.ExternalArticleCreateRequest;
import com.ruoyi.blog.external.dto.ExternalArticlePageQuery;
import com.ruoyi.blog.external.exception.BlogApiException;
import com.ruoyi.blog.external.mapper.BlogApiIdempotencyMapper;
import com.ruoyi.blog.external.security.BlogApiPrincipal;
import com.ruoyi.blog.external.security.BlogApiRequestContext;
import com.ruoyi.blog.external.vo.ExternalArticleCreateResult;
import com.ruoyi.blog.external.vo.ExternalArticleSummaryVO;
import com.ruoyi.blog.external.vo.ExternalArticleVO;
import com.ruoyi.blog.external.vo.ExternalCategoryVO;
import com.ruoyi.blog.external.vo.ExternalPageResponse;
import com.ruoyi.blog.external.vo.ExternalTagVO;
import com.ruoyi.blog.mapper.BlogArticleMapper;
import com.ruoyi.blog.mapper.BlogArticleTagMapper;
import com.ruoyi.blog.mapper.BlogCategoryMapper;
import com.ruoyi.blog.mapper.BlogTagMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExternalBlogArticleService
{
    private static final String SOURCE_EXTERNAL_API = "EXTERNAL_API";
    private static final int STATUS_DRAFT = 0;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    private final BlogArticleMapper articleMapper;
    private final BlogArticleTagMapper articleTagMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final BlogApiIdempotencyMapper idempotencyMapper;
    private final BlogExternalApiProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExternalArticleCreateResult createDraft(BlogApiPrincipal principal, String idempotencyKey,
            ExternalArticleCreateRequest request, HttpServletRequest servletRequest)
    {
        validateIdempotencyKey(idempotencyKey);
        normalizeAndValidate(request);
        String requestHash = requestHash(request);
        servletRequest.setAttribute(BlogApiRequestContext.ATTR_REQUEST_BODY_HASH, requestHash);

        BlogApiIdempotency existing = acquireIdempotency(principal.clientPk(), idempotencyKey, requestHash);
        if (existing != null)
        {
            ExternalArticleVO replay = replay(existing, principal.clientPk(), requestHash);
            servletRequest.setAttribute(BlogApiRequestContext.ATTR_ARTICLE_ID, replay.getId());
            return new ExternalArticleCreateResult(replay, true);
        }

        validateTaxonomy(request.getCategoryId(), request.getTagIds());
        if (StringUtils.hasText(request.getExternalId()))
        {
            Long duplicate = articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                    .eq(BlogArticle::getSourceClientId, principal.clientPk())
                    .eq(BlogArticle::getExternalId, request.getExternalId()));
            if (duplicate != null && duplicate > 0)
            {
                throw new BlogApiException(HttpStatus.CONFLICT, "external_id_conflict",
                        "externalId is already associated with another article");
            }
        }

        BlogArticle article = new BlogArticle();
        article.setTitle(request.getTitle());
        article.setSummary(blankToNull(request.getSummary()));
        article.setContent(request.getContentMarkdown());
        article.setCoverImage(blankToNull(request.getCoverImage()));
        article.setCategoryId(request.getCategoryId());
        article.setSourceType(SOURCE_EXTERNAL_API);
        article.setSourceClientId(principal.clientPk());
        article.setExternalId(blankToNull(request.getExternalId()));
        article.setStatus(STATUS_DRAFT);
        article.setIsAiGenerated(0);
        article.setViewCount(0);
        articleMapper.insert(article);

        List<Long> tagIds = normalizedTagIds(request.getTagIds());
        if (!tagIds.isEmpty())
        {
            articleTagMapper.batchInsert(article.getId(), tagIds);
        }

        BlogApiIdempotency success = idempotencyMapper.selectOne(new LambdaQueryWrapper<BlogApiIdempotency>()
                .eq(BlogApiIdempotency::getClientId, principal.clientPk())
                .eq(BlogApiIdempotency::getIdempotencyKey, idempotencyKey).last("LIMIT 1"));
        if (success == null)
        {
            throw new IllegalStateException("Idempotency reservation disappeared");
        }
        success.setStatus(1);
        success.setArticleId(article.getId());
        success.setHttpStatus(HttpStatus.CREATED.value());
        success.setResponseBody(null);
        success.setUpdateTime(LocalDateTime.now());
        idempotencyMapper.updateById(success);

        servletRequest.setAttribute(BlogApiRequestContext.ATTR_ARTICLE_ID, article.getId());
        return new ExternalArticleCreateResult(getOwned(principal.clientPk(), article.getId()), false);
    }

    public ExternalPageResponse<ExternalArticleSummaryVO> pageOwned(BlogApiPrincipal principal, ExternalArticlePageQuery query)
    {
        int pageNum = query.getPageNum() == null ? 1 : Math.max(1, query.getPageNum());
        int pageSize = query.getPageSize() == null ? 20 : Math.min(100, Math.max(1, query.getPageSize()));
        LambdaQueryWrapper<BlogArticle> wrapper = new LambdaQueryWrapper<BlogArticle>()
                .select(BlogArticle::getId, BlogArticle::getExternalId, BlogArticle::getTitle,
                        BlogArticle::getSummary, BlogArticle::getCoverImage, BlogArticle::getCategoryId,
                        BlogArticle::getStatus, BlogArticle::getCreateTime, BlogArticle::getUpdateTime)
                .eq(BlogArticle::getSourceType, SOURCE_EXTERNAL_API)
                .eq(BlogArticle::getSourceClientId, principal.clientPk());
        if (StringUtils.hasText(query.getKeyword()))
        {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(BlogArticle::getTitle, keyword).or().like(BlogArticle::getSummary, keyword));
        }
        wrapper.orderByDesc(BlogArticle::getUpdateTime).orderByDesc(BlogArticle::getId);
        Page<BlogArticle> result = articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<ExternalArticleSummaryVO> records = toSummaryVOs(result.getRecords());
        return new ExternalPageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ExternalArticleVO getOwned(Long clientPk, Long articleId)
    {
        BlogArticle article = articleMapper.selectOne(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getId, articleId)
                .eq(BlogArticle::getSourceType, SOURCE_EXTERNAL_API)
                .eq(BlogArticle::getSourceClientId, clientPk)
                .last("LIMIT 1"));
        if (article == null)
        {
            throw new BlogApiException(HttpStatus.NOT_FOUND, "article_not_found", "Article not found");
        }
        return toVOs(List.of(article)).get(0);
    }

    public List<ExternalCategoryVO> listCategories()
    {
        return categoryMapper.selectList(new LambdaQueryWrapper<BlogCategory>()
                .orderByAsc(BlogCategory::getSortOrder).orderByAsc(BlogCategory::getId)).stream()
                .map(category -> new ExternalCategoryVO(category.getId(), category.getName(), category.getSortOrder()))
                .toList();
    }

    public List<ExternalTagVO> listTags()
    {
        return tagMapper.selectList(new LambdaQueryWrapper<BlogTag>()
                .orderByAsc(BlogTag::getName).orderByAsc(BlogTag::getId)).stream()
                .map(tag -> new ExternalTagVO(tag.getId(), tag.getName()))
                .toList();
    }

    private BlogApiIdempotency acquireIdempotency(Long clientPk, String key, String hash)
    {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusHours(Math.max(1, properties.getIdempotencyTtlHours()));
        int inserted = idempotencyMapper.insertPending(clientPk, key, hash, expires);
        if (inserted == 1)
        {
            return null;
        }
        BlogApiIdempotency existing = findIdempotency(clientPk, key);
        if (existing != null && existing.getExpireTime() != null && existing.getExpireTime().isBefore(now))
        {
            idempotencyMapper.deleteExpiredKey(clientPk, key, now);
            if (idempotencyMapper.insertPending(clientPk, key, hash, expires) == 1)
            {
                return null;
            }
            existing = findIdempotency(clientPk, key);
        }
        if (existing == null)
        {
            throw new BlogApiException(HttpStatus.CONFLICT, "request_in_progress", "Request is being processed", 2);
        }
        return existing;
    }

    private BlogApiIdempotency findIdempotency(Long clientPk, String key)
    {
        return idempotencyMapper.selectOne(new LambdaQueryWrapper<BlogApiIdempotency>()
                .eq(BlogApiIdempotency::getClientId, clientPk)
                .eq(BlogApiIdempotency::getIdempotencyKey, key).last("LIMIT 1"));
    }

    private ExternalArticleVO replay(BlogApiIdempotency record, Long clientPk, String requestHash)
    {
        if (!requestHash.equals(record.getRequestHash()))
        {
            throw new BlogApiException(HttpStatus.CONFLICT, "idempotency_key_reused",
                    "Idempotency-Key was already used with a different request");
        }
        if (!Integer.valueOf(1).equals(record.getStatus()) || record.getArticleId() == null)
        {
            throw new BlogApiException(HttpStatus.CONFLICT, "request_in_progress", "Request is being processed", 2);
        }
        try
        {
            return getOwned(clientPk, record.getArticleId());
        }
        catch (BlogApiException e)
        {
            throw new BlogApiException(HttpStatus.CONFLICT, "idempotent_result_unavailable",
                    "The original result is no longer available");
        }
    }

    private void normalizeAndValidate(ExternalArticleCreateRequest request)
    {
        request.setTitle(request.getTitle().trim());
        request.setSummary(trim(request.getSummary()));
        request.setCoverImage(trim(request.getCoverImage()));
        request.setExternalId(trim(request.getExternalId()));
        if (request.getTitle().isEmpty())
        {
            throw new BlogApiException(HttpStatus.BAD_REQUEST, "invalid_title", "title is required");
        }
        int contentBytes = request.getContentMarkdown().getBytes(StandardCharsets.UTF_8).length;
        if (contentBytes > properties.getMaxBodyBytes())
        {
            throw new BlogApiException(HttpStatus.PAYLOAD_TOO_LARGE, "content_too_large",
                    "contentMarkdown exceeds the configured size limit");
        }
        validateCoverImage(request.getCoverImage());
    }

    private void validateCoverImage(String coverImage)
    {
        if (!StringUtils.hasText(coverImage) || coverImage.startsWith("/uploads/"))
        {
            return;
        }
        try
        {
            URI uri = URI.create(coverImage);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null)
            {
                throw new IllegalArgumentException("Only HTTPS URLs are allowed");
            }
        }
        catch (IllegalArgumentException e)
        {
            throw new BlogApiException(HttpStatus.BAD_REQUEST, "invalid_cover_image",
                    "coverImage must be an /uploads/ path or an HTTPS URL");
        }
    }

    private void validateTaxonomy(Long categoryId, List<Long> requestedTagIds)
    {
        if (categoryId != null && categoryMapper.selectById(categoryId) == null)
        {
            throw new BlogApiException(HttpStatus.BAD_REQUEST, "invalid_category", "categoryId does not exist");
        }
        List<Long> tagIds = normalizedTagIds(requestedTagIds);
        if (!tagIds.isEmpty())
        {
            Long count = tagMapper.selectCount(new LambdaQueryWrapper<BlogTag>().in(BlogTag::getId, tagIds));
            if (count == null || count != tagIds.size())
            {
                throw new BlogApiException(HttpStatus.BAD_REQUEST, "invalid_tags", "One or more tagIds do not exist");
            }
        }
    }

    private List<Long> normalizedTagIds(List<Long> tagIds)
    {
        if (CollectionUtils.isEmpty(tagIds))
        {
            return List.of();
        }
        Set<Long> values = new LinkedHashSet<>(tagIds);
        if (values.size() > 10)
        {
            throw new BlogApiException(HttpStatus.BAD_REQUEST, "too_many_tags", "At most 10 tags are allowed");
        }
        return List.copyOf(values);
    }

    private List<ExternalArticleVO> toVOs(List<BlogArticle> articles)
    {
        if (articles.isEmpty())
        {
            return List.of();
        }
        List<Long> articleIds = articles.stream().map(BlogArticle::getId).toList();
        List<Long> categoryIds = articles.stream().map(BlogArticle::getCategoryId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> categories = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectList(new LambdaQueryWrapper<BlogCategory>().in(BlogCategory::getId, categoryIds))
                        .stream().collect(Collectors.toMap(BlogCategory::getId, BlogCategory::getName));
        Map<Long, List<BlogTag>> tags = new HashMap<>();
        for (ArticleTagRow row : articleTagMapper.selectTagsByArticleIds(articleIds))
        {
            BlogTag tag = new BlogTag();
            tag.setId(row.getId());
            tag.setName(row.getName());
            tags.computeIfAbsent(row.getArticleId(), ignored -> new ArrayList<>()).add(tag);
        }
        return articles.stream().map(article -> {
            ExternalArticleVO vo = new ExternalArticleVO();
            vo.setId(article.getId());
            vo.setExternalId(article.getExternalId());
            vo.setTitle(article.getTitle());
            vo.setSummary(article.getSummary());
            vo.setContentMarkdown(article.getContent());
            vo.setCoverImage(article.getCoverImage());
            vo.setCategoryId(article.getCategoryId());
            vo.setCategoryName(categories.get(article.getCategoryId()));
            List<BlogTag> articleTags = tags.getOrDefault(article.getId(), List.of());
            vo.setTagIds(articleTags.stream().map(BlogTag::getId).toList());
            vo.setTagNames(articleTags.stream().map(BlogTag::getName).toList());
            vo.setStatus(article.getStatus());
            vo.setCreateTime(article.getCreateTime());
            vo.setUpdateTime(article.getUpdateTime());
            return vo;
        }).toList();
    }

    private List<ExternalArticleSummaryVO> toSummaryVOs(List<BlogArticle> articles)
    {
        return toVOs(articles).stream().map(article -> {
            ExternalArticleSummaryVO summary = new ExternalArticleSummaryVO();
            summary.setId(article.getId());
            summary.setExternalId(article.getExternalId());
            summary.setTitle(article.getTitle());
            summary.setSummary(article.getSummary());
            summary.setCoverImage(article.getCoverImage());
            summary.setCategoryId(article.getCategoryId());
            summary.setCategoryName(article.getCategoryName());
            summary.setTagIds(article.getTagIds());
            summary.setTagNames(article.getTagNames());
            summary.setStatus(article.getStatus());
            summary.setCreateTime(article.getCreateTime());
            summary.setUpdateTime(article.getUpdateTime());
            return summary;
        }).toList();
    }

    private String requestHash(ExternalArticleCreateRequest request)
    {
        try
        {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        }
        catch (JsonProcessingException | NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("Unable to hash request", e);
        }
    }

    private void validateIdempotencyKey(String key)
    {
        if (!StringUtils.hasText(key) || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches())
        {
            throw new BlogApiException(HttpStatus.BAD_REQUEST, "invalid_idempotency_key",
                    "Idempotency-Key must be 8-128 URL-safe characters");
        }
    }

    private String trim(String value)
    {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value)
    {
        return StringUtils.hasText(value) ? value : null;
    }
}
