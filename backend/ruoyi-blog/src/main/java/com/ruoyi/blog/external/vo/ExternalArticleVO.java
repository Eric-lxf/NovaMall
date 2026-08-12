package com.ruoyi.blog.external.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class ExternalArticleVO
{
    private Long id;
    private String externalId;
    private String title;
    private String summary;
    private String contentMarkdown;
    private String coverImage;
    private Long categoryId;
    private String categoryName;
    private List<Long> tagIds;
    private List<String> tagNames;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
