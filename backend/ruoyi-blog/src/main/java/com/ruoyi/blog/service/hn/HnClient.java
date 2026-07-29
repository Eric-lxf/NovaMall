package com.ruoyi.blog.service.hn;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.blog.constant.HnBoard;
import com.ruoyi.blog.dto.hn.HnItemDto;
import com.ruoyi.common.exception.ServiceException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Component
public class HnClient
{
    private static final String BASE_URL = "https://hacker-news.firebaseio.com/v0/";
    private static final int MAX_RETRIES = 2;
    private static final TypeReference<List<Long>> STORY_ID_LIST = new TypeReference<>()
    {
    };

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore rateLimiter = new Semaphore(5);

    public HnClient(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public List<Long> fetchStoryIds(HnBoard board)
    {
        String url = BASE_URL + board.getPath() + ".json";
        String body = executeGet(url, true);
        if (body == null || body.isBlank() || "null".equals(body.trim()))
        {
            return Collections.emptyList();
        }
        try
        {
            List<Long> ids = objectMapper.readValue(body, STORY_ID_LIST);
            if (ids == null || ids.isEmpty())
            {
                return Collections.emptyList();
            }
            int cap = board.getCap();
            return ids.size() <= cap ? ids : ids.subList(0, cap);
        }
        catch (IOException e)
        {
            throw new ServiceException("解析 HN 榜单失败: " + e.getMessage());
        }
    }

    public HnItemDto fetchItem(long hnId)
    {
        String url = BASE_URL + "item/" + hnId + ".json";
        String body = executeGet(url, true);
        if (body == null || body.isBlank() || "null".equals(body.trim()))
        {
            return null;
        }
        try
        {
            HnItemDto item = objectMapper.readValue(body, HnItemDto.class);
            if (item == null || item.getId() == null)
            {
                return null;
            }
            return item;
        }
        catch (IOException e)
        {
            throw new ServiceException("解析 HN 条目失败: " + e.getMessage());
        }
    }

    private String executeGet(String url, boolean throwOnFailure)
    {
        IOException lastIo = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++)
        {
            try
            {
                rateLimiter.acquire();
                try
                {
                    Request request = new Request.Builder().url(url).get().build();
                    try (Response response = httpClient.newCall(request).execute())
                    {
                        if (response.code() == 404)
                        {
                            return null;
                        }
                        if (!response.isSuccessful())
                        {
                            if (attempt < MAX_RETRIES)
                            {
                                continue;
                            }
                            if (throwOnFailure)
                            {
                                throw new ServiceException("HN API 请求失败: HTTP " + response.code());
                            }
                            return null;
                        }
                        ResponseBody responseBody = response.body();
                        return responseBody != null ? responseBody.string() : null;
                    }
                }
                finally
                {
                    rateLimiter.release();
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new ServiceException("HN API 请求被中断");
            }
            catch (IOException e)
            {
                lastIo = e;
                if (attempt >= MAX_RETRIES)
                {
                    break;
                }
            }
        }
        if (throwOnFailure)
        {
            throw new ServiceException("HN API 请求失败: " + (lastIo != null ? lastIo.getMessage() : "unknown"));
        }
        return null;
    }
}
