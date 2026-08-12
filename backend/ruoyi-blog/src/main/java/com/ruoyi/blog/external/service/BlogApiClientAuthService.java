package com.ruoyi.blog.external.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.blog.domain.BlogApiClient;
import com.ruoyi.blog.external.config.BlogExternalApiProperties;
import com.ruoyi.blog.external.constant.BlogApiScopes;
import com.ruoyi.blog.external.exception.BlogApiException;
import com.ruoyi.blog.external.security.BlogApiPrincipal;
import com.ruoyi.blog.external.security.BlogApiTokenSession;
import com.ruoyi.blog.external.service.BlogApiOpaqueTokenService.IssuedToken;
import com.ruoyi.blog.external.vo.BlogTokenResponse;
import com.ruoyi.blog.mapper.BlogApiClientMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlogApiClientAuthService
{
    private static final String STATUS_ENABLED = "0";
    /** 有效 BCrypt 值，用于不存在的 client，降低 client_id 时序枚举风险。 */
    private static final String DUMMY_BCRYPT = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.6JMyK6eqo3W7Ypt05ODZk1zMqEmoM7W";

    private final BlogApiClientMapper clientMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final BlogApiOpaqueTokenService opaqueTokenService;
    private final BlogApiRateLimiter rateLimiter;
    private final BlogExternalApiProperties properties;

    public TokenGrant issueToken(String clientId, String clientSecret, String requestedScope,
            HttpServletRequest request)
    {
        validateCredentialShape(clientId, clientSecret);
        String sourceIpHash = sha256(sourceIp(request));
        // 先按来源限制 BCrypt 总成本；client+IP 桶不会让单一攻击来源锁死所有合法调用方。
        rateLimiter.check("token:ip:" + sourceIpHash, 30, 60);
        rateLimiter.check("token:credential:" + sha256(clientId + ":" + sourceIpHash), 10, 60);

        BlogApiClient client = clientMapper.selectOne(new LambdaQueryWrapper<BlogApiClient>()
                .eq(BlogApiClient::getClientId, clientId).last("LIMIT 1"));
        String storedHash = client == null || !StringUtils.hasText(client.getClientSecretHash())
                ? DUMMY_BCRYPT : client.getClientSecretHash();
        boolean secretMatches;
        try
        {
            secretMatches = passwordEncoder.matches(clientSecret, storedHash);
        }
        catch (RuntimeException e)
        {
            secretMatches = false;
        }
        if (client == null || !secretMatches || !STATUS_ENABLED.equals(client.getStatus()))
        {
            throw new BlogApiException(HttpStatus.UNAUTHORIZED, "invalid_client", "Invalid client credentials");
        }

        // 成功签发独立按客户端限制，错误凭证不会消耗合法客户端的成功额度。
        rateLimiter.check("token:issued:" + client.getId(), 10, 60);

        List<String> configuredScopes = BlogApiScopes.parse(client.getScopes());
        List<String> grantedScopes = StringUtils.hasText(requestedScope)
                ? BlogApiScopes.parse(requestedScope) : configuredScopes;
        if (grantedScopes.isEmpty() || !BlogApiScopes.SUPPORTED.containsAll(grantedScopes)
                || !configuredScopes.containsAll(grantedScopes))
        {
            throw new BlogApiException(HttpStatus.BAD_REQUEST, "invalid_scope", "Requested scope is not allowed");
        }

        int configuredTtl = client.getTokenTtlSeconds() == null
                ? properties.getTokenTtlSeconds() : client.getTokenTtlSeconds();
        int ttl = Math.max(60, Math.min(configuredTtl, 86_400));
        IssuedToken issued = opaqueTokenService.issue(client, grantedScopes, ttl);
        clientMapper.update(null, new LambdaUpdateWrapper<BlogApiClient>()
                .eq(BlogApiClient::getId, client.getId())
                .set(BlogApiClient::getLastUsedTime, LocalDateTime.now()));

        BlogTokenResponse response = new BlogTokenResponse(issued.rawToken(), "Bearer", issued.expiresIn(),
                String.join(" ", grantedScopes));
        return new TokenGrant(response, client.getId(), client.getSecretVersion());
    }

    public BlogApiPrincipal validateSession(BlogApiTokenSession session)
    {
        if (session == null || session.getClientPk() == null || !StringUtils.hasText(session.getClientId()))
        {
            return null;
        }
        BlogApiClient client = clientMapper.selectById(session.getClientPk());
        if (client == null || !STATUS_ENABLED.equals(client.getStatus())
                || !session.getClientId().equals(client.getClientId())
                || client.getSecretVersion() == null
                || !client.getSecretVersion().equals(session.getSecretVersion()))
        {
            return null;
        }
        List<String> currentScopes = BlogApiScopes.parse(client.getScopes());
        List<String> tokenScopes = session.getScopes() == null ? List.of() : List.copyOf(session.getScopes());
        if (tokenScopes.isEmpty() || !currentScopes.containsAll(tokenScopes))
        {
            return null;
        }
        int rateLimit = client.getRateLimitPerMinute() == null
                ? properties.getDefaultRateLimitPerMinute() : client.getRateLimitPerMinute();
        return new BlogApiPrincipal(client.getId(), client.getClientId(), client.getClientName(), tokenScopes,
                Math.max(1, rateLimit));
    }

    private void validateCredentialShape(String clientId, String clientSecret)
    {
        if (!StringUtils.hasText(clientId) || clientId.length() > 64
                || !StringUtils.hasText(clientSecret) || clientSecret.length() > 200)
        {
            throw new BlogApiException(HttpStatus.UNAUTHORIZED, "invalid_client", "Invalid client credentials");
        }
    }

    private String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String sourceIp(HttpServletRequest request)
    {
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }

    public record TokenGrant(BlogTokenResponse response, Long clientPk, Integer secretVersion)
    {
    }
}
