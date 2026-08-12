package com.ruoyi.blog.external.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("blog_api_audit")
public class BlogApiAudit
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long clientId;
    private Integer secretVersion;
    private String idempotencyKey;
    private String requestMethod;
    private String requestPath;
    private String sourceIp;
    private String requestBodyHash;
    private String authResult;
    private Integer httpStatus;
    private String errorCode;
    private Long articleId;
    private Long costTimeMs;
    private LocalDateTime createTime;
}
