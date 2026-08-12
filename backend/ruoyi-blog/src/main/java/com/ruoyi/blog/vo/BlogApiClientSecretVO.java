package com.ruoyi.blog.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 客户端创建或密钥轮换结果。clientSecret 只在本次响应返回。
 */
@Data
public class BlogApiClientSecretVO
{
    private Long id;
    private String clientId;
    private String clientName;
    private String clientSecret;
    private List<String> scopes;
    private String status;
    private Integer secretVersion;
    private Integer rateLimitPerMinute;
    private Integer tokenTtlSeconds;
    private LocalDateTime secretRotatedTime;
}
