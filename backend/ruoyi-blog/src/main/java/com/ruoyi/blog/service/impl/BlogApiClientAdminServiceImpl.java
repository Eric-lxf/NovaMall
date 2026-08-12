package com.ruoyi.blog.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.domain.BlogApiClient;
import com.ruoyi.blog.dto.BlogApiClientCreateRequest;
import com.ruoyi.blog.dto.BlogApiClientPageQuery;
import com.ruoyi.blog.dto.BlogApiClientStatusRequest;
import com.ruoyi.blog.mapper.BlogApiClientMapper;
import com.ruoyi.blog.service.BlogApiClientAdminService;
import com.ruoyi.blog.vo.BlogApiClientSecretVO;
import com.ruoyi.blog.vo.BlogApiClientVO;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlogApiClientAdminServiceImpl implements BlogApiClientAdminService
{
    private static final String STATUS_ENABLED = "0";
    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    private static final int DEFAULT_TOKEN_TTL_SECONDS = 900;
    private static final int CLIENT_ID_RANDOM_BYTES = 16;
    private static final int CLIENT_SECRET_RANDOM_BYTES = 32;
    private static final int CLIENT_ID_GENERATION_ATTEMPTS = 5;
    private static final String CLIENT_ID_PREFIX = "blog_";
    private static final Set<String> ALLOWED_SCOPES = Set.of(
            "blog.article.create",
            "blog.article.read.own",
            "blog.taxonomy.read");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final BlogApiClientMapper blogApiClientMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public Page<BlogApiClientVO> page(BlogApiClientPageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        Page<BlogApiClient> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BlogApiClient> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword()))
        {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(BlogApiClient::getClientName, keyword)
                    .or().like(BlogApiClient::getClientId, keyword));
        }
        if (query.getStatus() != null)
        {
            wrapper.eq(BlogApiClient::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(BlogApiClient::getCreateTime).orderByDesc(BlogApiClient::getId);

        Page<BlogApiClient> result = blogApiClientMapper.selectPage(page, wrapper);
        Page<BlogApiClientVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    @Transactional
    public BlogApiClientSecretVO create(BlogApiClientCreateRequest request)
    {
        LocalDateTime now = LocalDateTime.now();
        String operator = SecurityUtils.getUsername();
        String rawSecret = randomUrlSafeValue(CLIENT_SECRET_RANDOM_BYTES);

        BlogApiClient client = new BlogApiClient();
        client.setClientId(generateUniqueClientId());
        client.setClientName(request.getClientName().trim());
        client.setClientSecretHash(passwordEncoder.encode(rawSecret));
        client.setSecretVersion(1);
        client.setScopes(normalizeScopes(request.getScopes()));
        client.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        client.setRateLimitPerMinute(request.getRateLimitPerMinute() == null
                ? DEFAULT_RATE_LIMIT_PER_MINUTE : request.getRateLimitPerMinute());
        client.setTokenTtlSeconds(request.getTokenTtlSeconds() == null
                ? DEFAULT_TOKEN_TTL_SECONDS : request.getTokenTtlSeconds());
        client.setSecretRotatedTime(now);
        client.setCreateBy(operator);
        client.setCreateTime(now);
        client.setUpdateBy(operator);
        client.setUpdateTime(now);
        client.setRemark(trimToNull(request.getRemark()));

        if (blogApiClientMapper.insert(client) != 1)
        {
            throw new ServiceException("API客户端创建失败", HttpStatus.ERROR);
        }
        return toSecretVO(client, rawSecret);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, BlogApiClientStatusRequest request)
    {
        requireClient(id);
        int updated = blogApiClientMapper.update(null, new LambdaUpdateWrapper<BlogApiClient>()
                .eq(BlogApiClient::getId, id)
                .set(BlogApiClient::getStatus, request.getStatus())
                .set(BlogApiClient::getUpdateBy, SecurityUtils.getUsername())
                .set(BlogApiClient::getUpdateTime, LocalDateTime.now()));
        if (updated != 1)
        {
            throw new ServiceException("API客户端状态更新失败", HttpStatus.ERROR);
        }
    }

    @Override
    @Transactional
    public BlogApiClientSecretVO rotateSecret(Long id)
    {
        BlogApiClient existing = requireClient(id);
        String oldHash = existing.getClientSecretHash();
        if (!StringUtils.hasText(oldHash))
        {
            throw new ServiceException("API客户端密钥状态异常", HttpStatus.ERROR);
        }
        Integer oldVersion = existing.getSecretVersion();
        if (oldVersion == null || oldVersion < 1 || oldVersion == Integer.MAX_VALUE)
        {
            throw new ServiceException("API客户端密钥版本异常", HttpStatus.ERROR);
        }
        int newVersion = oldVersion + 1;

        String rawSecret = randomUrlSafeValue(CLIENT_SECRET_RANDOM_BYTES);
        String newHash = passwordEncoder.encode(rawSecret);
        LocalDateTime now = LocalDateTime.now();
        String operator = SecurityUtils.getUsername();

        int updated = blogApiClientMapper.update(null, new LambdaUpdateWrapper<BlogApiClient>()
                .eq(BlogApiClient::getId, id)
                .eq(BlogApiClient::getClientSecretHash, oldHash)
                .eq(BlogApiClient::getSecretVersion, oldVersion)
                .set(BlogApiClient::getClientSecretHash, newHash)
                .set(BlogApiClient::getSecretVersion, newVersion)
                .set(BlogApiClient::getSecretRotatedTime, now)
                .set(BlogApiClient::getUpdateBy, operator)
                .set(BlogApiClient::getUpdateTime, now));
        if (updated != 1)
        {
            throw new ServiceException("密钥已被同时轮换，请刷新后重试", HttpStatus.CONFLICT);
        }

        existing.setClientSecretHash(newHash);
        existing.setSecretVersion(newVersion);
        existing.setSecretRotatedTime(now);
        existing.setUpdateBy(operator);
        existing.setUpdateTime(now);
        return toSecretVO(existing, rawSecret);
    }

    private BlogApiClient requireClient(Long id)
    {
        BlogApiClient client = blogApiClientMapper.selectById(id);
        if (client == null)
        {
            throw new ServiceException("API客户端不存在", HttpStatus.NOT_FOUND);
        }
        return client;
    }

    private String generateUniqueClientId()
    {
        for (int attempt = 0; attempt < CLIENT_ID_GENERATION_ATTEMPTS; attempt++)
        {
            String clientId = CLIENT_ID_PREFIX + randomUrlSafeValue(CLIENT_ID_RANDOM_BYTES);
            Long count = blogApiClientMapper.selectCount(new LambdaQueryWrapper<BlogApiClient>()
                    .eq(BlogApiClient::getClientId, clientId));
            if (count == null || count == 0)
            {
                return clientId;
            }
        }
        throw new ServiceException("无法生成唯一的客户端标识，请重试", HttpStatus.ERROR);
    }

    private String randomUrlSafeValue(int byteLength)
    {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    private String normalizeScopes(List<String> scopes)
    {
        if (scopes == null || scopes.isEmpty())
        {
            throw new ServiceException("至少需要配置一个权限范围", HttpStatus.BAD_REQUEST);
        }
        List<String> normalized = scopes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
        if (normalized.isEmpty())
        {
            throw new ServiceException("至少需要配置一个权限范围", HttpStatus.BAD_REQUEST);
        }
        List<String> unsupported = normalized.stream()
                .filter(scope -> !ALLOWED_SCOPES.contains(scope))
                .toList();
        if (!unsupported.isEmpty())
        {
            throw new ServiceException("包含不支持的权限范围: " + String.join(",", unsupported),
                    HttpStatus.BAD_REQUEST);
        }
        return normalized.stream().collect(Collectors.joining(","));
    }

    private List<String> parseScopes(String scopes)
    {
        if (!StringUtils.hasText(scopes))
        {
            return List.of();
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String trimToNull(String value)
    {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BlogApiClientVO toVO(BlogApiClient client)
    {
        BlogApiClientVO vo = new BlogApiClientVO();
        vo.setId(client.getId());
        vo.setClientId(client.getClientId());
        vo.setClientName(client.getClientName());
        vo.setScopes(parseScopes(client.getScopes()));
        vo.setStatus(client.getStatus());
        vo.setSecretVersion(client.getSecretVersion());
        vo.setRateLimitPerMinute(client.getRateLimitPerMinute());
        vo.setTokenTtlSeconds(client.getTokenTtlSeconds());
        vo.setLastUsedTime(client.getLastUsedTime());
        vo.setSecretRotatedTime(client.getSecretRotatedTime());
        vo.setCreateBy(client.getCreateBy());
        vo.setCreateTime(client.getCreateTime());
        vo.setUpdateBy(client.getUpdateBy());
        vo.setUpdateTime(client.getUpdateTime());
        vo.setRemark(client.getRemark());
        return vo;
    }

    private BlogApiClientSecretVO toSecretVO(BlogApiClient client, String rawSecret)
    {
        BlogApiClientSecretVO vo = new BlogApiClientSecretVO();
        vo.setId(client.getId());
        vo.setClientId(client.getClientId());
        vo.setClientName(client.getClientName());
        vo.setClientSecret(rawSecret);
        vo.setScopes(parseScopes(client.getScopes()));
        vo.setStatus(client.getStatus());
        vo.setSecretVersion(client.getSecretVersion());
        vo.setRateLimitPerMinute(client.getRateLimitPerMinute());
        vo.setTokenTtlSeconds(client.getTokenTtlSeconds());
        vo.setSecretRotatedTime(client.getSecretRotatedTime());
        return vo;
    }
}
