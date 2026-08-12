package com.ruoyi.blog.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("blog_article")
public class BlogArticle
{

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String summary;
    private String content;
    private String htmlContent;
    private String coverImage;
    private Long categoryId;
    private Long authorUserId;
    /** ADMIN / EXTERNAL_API */
    private String sourceType;
    private Long sourceClientId;
    private String externalId;
    /** 0-草稿, 1-已发布, 2-AI生成中 */
    private Integer status;
    private Integer isAiGenerated;
    private Integer viewCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
