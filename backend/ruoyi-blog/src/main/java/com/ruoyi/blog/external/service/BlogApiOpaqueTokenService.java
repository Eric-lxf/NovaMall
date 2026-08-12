package com.ruoyi.blog.external.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.ruoyi.blog.domain.BlogApiClient;
import com.ruoyi.blog.external.security.BlogApiTokenSession;
import com.ruoyi.common.core.redis.RedisCache;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlogApiOpaqueTokenService
{
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final String TOKEN_PREFIX = "nmb_";
    private static final String REDIS_PREFIX = "blog:external:token:";

    private final RedisCache redisCache;

    public IssuedToken issue(BlogApiClient client, java.util.List<String> scopes, int ttlSeconds)
    {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        String rawToken = TOKEN_PREFIX + BASE64_URL.encodeToString(random);
        long now = Instant.now().getEpochSecond();

        BlogApiTokenSession session = new BlogApiTokenSession();
        session.setClientPk(client.getId());
        session.setClientId(client.getClientId());
        session.setClientName(client.getClientName());
        session.setScopes(java.util.List.copyOf(scopes));
        session.setSecretVersion(client.getSecretVersion());
        session.setRateLimitPerMinute(client.getRateLimitPerMinute());
        session.setIssuedAtEpochSecond(now);
        session.setExpiresAtEpochSecond(now + ttlSeconds);
        redisCache.setCacheObject(redisKey(rawToken), session, ttlSeconds, TimeUnit.SECONDS);
        return new IssuedToken(rawToken, ttlSeconds, session);
    }

    public BlogApiTokenSession resolve(String rawToken)
    {
        if (rawToken == null || rawToken.length() < 40 || rawToken.length() > 128 || !rawToken.startsWith(TOKEN_PREFIX))
        {
            return null;
        }
        return redisCache.getCacheObject(redisKey(rawToken));
    }

    private String redisKey(String rawToken)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return REDIS_PREFIX + java.util.HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record IssuedToken(String rawToken, int expiresIn, BlogApiTokenSession session)
    {
    }
}
