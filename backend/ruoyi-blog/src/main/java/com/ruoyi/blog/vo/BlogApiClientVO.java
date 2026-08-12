package com.ruoyi.blog.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class BlogApiClientVO
{
    private Long id;
    private String clientId;
    private String clientName;
    private List<String> scopes;
    private String status;
    private Integer secretVersion;
    private Integer rateLimitPerMinute;
    private Integer tokenTtlSeconds;
    private LocalDateTime lastUsedTime;
    private LocalDateTime secretRotatedTime;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}
