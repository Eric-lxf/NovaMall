package com.ruoyi.blog.external.service;

import java.util.Collections;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ruoyi.blog.external.exception.BlogApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlogApiRateLimiter
{
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            if current > tonumber(ARGV[1]) then return 0 end
            return 1
            """, Long.class);

    private final RedisTemplate<Object, Object> redisTemplate;

    public void check(String key, int limit, int windowSeconds)
    {
        int safeLimit = Math.max(1, limit);
        int safeWindow = Math.max(1, windowSeconds);
        try
        {
            Long allowed = redisTemplate.execute(SCRIPT, Collections.singletonList("blog:external:rate:" + key),
                    safeLimit, safeWindow);
            if (allowed == null || allowed == 0)
            {
                throw new BlogApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded",
                        "Too many requests", safeWindow);
            }
        }
        catch (BlogApiException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new BlogApiException(HttpStatus.SERVICE_UNAVAILABLE, "rate_limiter_unavailable",
                    "Rate limiter is temporarily unavailable");
        }
    }
}
