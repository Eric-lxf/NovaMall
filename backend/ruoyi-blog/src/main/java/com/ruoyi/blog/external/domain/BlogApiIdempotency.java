package com.ruoyi.blog.external.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("blog_api_idempotency")
public class BlogApiIdempotency
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long clientId;
    private String idempotencyKey;
    private String requestHash;
    /** 0 处理中 / 1 成功 / 2 失败。 */
    private Integer status;
    private Long articleId;
    private Integer httpStatus;
    private String responseBody;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
