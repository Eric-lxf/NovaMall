package com.ruoyi.blog.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 外部博客 API 客户端。
 */
@Data
@TableName("blog_api_client")
public class BlogApiClient
{
    @TableId(type = IdType.AUTO)
    private Long id;

    private String clientId;

    private String clientName;

    /** BCrypt 哈希，禁止通过管理接口序列化。 */
    @JsonIgnore
    private String clientSecretHash;

    /** 当前密钥版本，从 1 开始，每次轮换递增。 */
    private Integer secretVersion;

    /** 逗号分隔的 scope。 */
    private String scopes;

    /** RuoYi 状态：0 启用 / 1 停用。 */
    private String status;

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
