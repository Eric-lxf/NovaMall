package com.ruoyi.web.controller.wedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 吾喜备婚管理端内网代理配置
 */
@Component
@ConfigurationProperties(prefix = "wedding.admin")
public class WeddingAdminProperties
{
    /** 备婚后端基础地址，例如 http://127.0.0.1:8080 或同机 http://127.0.0.1:8081 */
    private String baseUrl = "http://127.0.0.1:8080";

    /** 与备婚后端 ADMIN_API_TOKEN 对齐的管理密钥 */
    private String token = "";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    public int getConnectTimeoutMs()
    {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs)
    {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs()
    {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs)
    {
        this.readTimeoutMs = readTimeoutMs;
    }
}
