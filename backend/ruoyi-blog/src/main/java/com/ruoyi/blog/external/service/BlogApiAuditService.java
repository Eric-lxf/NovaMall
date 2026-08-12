package com.ruoyi.blog.external.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ruoyi.blog.external.domain.BlogApiAudit;
import com.ruoyi.blog.external.mapper.BlogApiAuditMapper;
import com.ruoyi.blog.external.security.BlogApiRequestContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlogApiAuditService
{
    private static final Logger log = LoggerFactory.getLogger(BlogApiAuditService.class);

    private final BlogApiAuditMapper auditMapper;

    public void record(HttpServletRequest request, HttpServletResponse response)
    {
        try
        {
            BlogApiAudit audit = new BlogApiAudit();
            audit.setRequestId(limit(BlogApiRequestContext.requestId(request), 64));
            audit.setClientId(longAttribute(request, BlogApiRequestContext.ATTR_CLIENT_PK));
            audit.setSecretVersion(integerAttribute(request, BlogApiRequestContext.ATTR_SECRET_VERSION));
            audit.setIdempotencyKey(limit(request.getHeader(BlogApiRequestContext.IDEMPOTENCY_KEY_HEADER), 128));
            audit.setRequestMethod(limit(request.getMethod(), 10));
            audit.setRequestPath(limit(request.getRequestURI(), 255));
            audit.setSourceIp(limit(sourceIp(request), 64));
            audit.setRequestBodyHash(limit(stringAttribute(request, BlogApiRequestContext.ATTR_REQUEST_BODY_HASH), 64));
            audit.setAuthResult(Optional.ofNullable(stringAttribute(request, BlogApiRequestContext.ATTR_AUTH_RESULT))
                    .orElse("ANONYMOUS"));
            audit.setHttpStatus(response.getStatus());
            audit.setErrorCode(limit(stringAttribute(request, BlogApiRequestContext.ATTR_ERROR_CODE), 64));
            audit.setArticleId(longAttribute(request, BlogApiRequestContext.ATTR_ARTICLE_ID));
            Object started = request.getAttribute(BlogApiRequestContext.ATTR_STARTED_NANOS);
            if (started instanceof Long value)
            {
                audit.setCostTimeMs(Math.max(0L, (System.nanoTime() - value) / 1_000_000L));
            }
            audit.setCreateTime(LocalDateTime.now());
            auditMapper.insert(audit);
        }
        catch (Exception e)
        {
            // 审计失败不得改变已经完成的业务响应，但必须进入服务端告警日志。
            log.error("Failed to persist external blog API audit, requestId={}",
                    BlogApiRequestContext.requestId(request), e);
        }
    }

    private String sourceIp(HttpServletRequest request)
    {
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp.trim();
    }

    private String stringAttribute(HttpServletRequest request, String key)
    {
        Object value = request.getAttribute(key);
        return value instanceof String string ? string : null;
    }

    private Long longAttribute(HttpServletRequest request, String key)
    {
        Object value = request.getAttribute(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer integerAttribute(HttpServletRequest request, String key)
    {
        Object value = request.getAttribute(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private String limit(String value, int max)
    {
        if (value == null)
        {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
