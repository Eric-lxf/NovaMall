package com.ruoyi.blog.external.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/** 列表响应不返回正文，避免分页查询放大响应体。 */
@Data
public class ExternalArticleSummaryVO
{
    private Long id;
    private String externalId;
    private String title;
    private String summary;
    private String coverImage;
    private Long categoryId;
    private String categoryName;
    private List<Long> tagIds;
    private List<String> tagNames;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
