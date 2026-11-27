package com.ruoyi.web.controller.wedding;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.AjaxResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 吾喜备婚管理端 HTTP 代理服务（服务端注入 X-Admin-Token 并完成 ApiResponse -> AjaxResult 映射）
 */
@Service
public class WeddingAdminProxyService
{
    private static final Logger log = LoggerFactory.getLogger(WeddingAdminProxyService.class);

    private final WeddingAdminProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public WeddingAdminProxyService(WeddingAdminProperties properties)
    {
        this(properties, createRestTemplate(properties));
    }

    WeddingAdminProxyService(WeddingAdminProperties properties, RestTemplate restTemplate)
    {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate(WeddingAdminProperties props)
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, props.getConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(1000, props.getReadTimeoutMs()));
        return new RestTemplate(factory);
    }

    public AjaxResult get(String upstreamPath, Map<String, Object> queryParams)
    {
        return exchange(HttpMethod.GET, upstreamPath, queryParams, null);
    }

    public AjaxResult post(String upstreamPath, Object body)
    {
        return exchange(HttpMethod.POST, upstreamPath, null, body);
    }

    public AjaxResult put(String upstreamPath, Object body)
    {
        return exchange(HttpMethod.PUT, upstreamPath, null, body);
    }

    private AjaxResult exchange(HttpMethod method, String upstreamPath, Map<String, Object> queryParams, Object body)
    {
        String token = properties.getToken();
        if (token == null || token.trim().isEmpty())
        {
            return AjaxResult.error("未配置吾喜备婚管理密钥 WEDDING_ADMIN_TOKEN");
        }
        String base = properties.getBaseUrl();
        if (base == null || base.trim().isEmpty())
        {
            return AjaxResult.error("未配置吾喜备婚服务地址 WEDDING_ADMIN_BASE_URL");
        }

        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base.trim();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(normalizedBase + upstreamPath);
        if (queryParams != null)
        {
            for (Map.Entry<String, Object> entry : queryParams.entrySet())
            {
                if (entry.getValue() != null && !String.valueOf(entry.getValue()).trim().isEmpty())
                {
                    uriBuilder.queryParam(entry.getKey(), entry.getValue());
                }
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", token.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

        try
        {
            ResponseEntity<String> resp = restTemplate.exchange(
                    uriBuilder.build().encode(StandardCharsets.UTF_8).toUri(),
                    method,
                    entity,
                    String.class);
            return parseUpstreamPayload(resp.getStatusCode().value(), resp.getBody());
        }
        catch (HttpStatusCodeException httpEx)
        {
            return parseUpstreamPayload(httpEx.getStatusCode().value(), httpEx.getResponseBodyAsString());
        }
        catch (ResourceAccessException connEx)
        {
            log.warn("无法连接吾喜备婚后端: base={}, err={}", normalizedBase, connEx.getMessage());
            return AjaxResult.error(503, "无法连接吾喜备婚后端服务，请检查服务状态");
        }
        catch (Exception ex)
        {
            log.error("调用吾喜备婚管理接口异常: path={}", upstreamPath, ex);
            return AjaxResult.error("调用吾喜备婚管理接口异常，请稍后重试");
        }
    }

    private AjaxResult parseUpstreamPayload(int httpStatus, String rawBody)
    {
        if (rawBody == null || rawBody.trim().isEmpty())
        {
            return httpStatus == 200 ? AjaxResult.success() : AjaxResult.error(httpStatus, "上游服务未返回内容");
        }
        try
        {
            Map<String, Object> map = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
            int code = map.get("code") instanceof Number n ? n.intValue() : httpStatus;
            String message = map.get("message") != null ? String.valueOf(map.get("message")) : "OK";
            Object data = map.get("data");
            if (code == 200)
            {
                return AjaxResult.success(message, data);
            }
            if (code == 401)
            {
                // 避免被 RuoYi 前端响应拦截器误判为后台自身登录会话过期
                return AjaxResult.error(502, "备婚后端管理密钥校验未通过，请核对 WEDDING_ADMIN_TOKEN");
            }
            return AjaxResult.error(code, message);
        }
        catch (Exception parseEx)
        {
            log.warn("解析吾喜备婚响应失败: httpStatus={}, err={}", httpStatus, parseEx.getMessage());
            return AjaxResult.error(httpStatus, "解析吾喜备婚响应失败");
        }
    }
}
