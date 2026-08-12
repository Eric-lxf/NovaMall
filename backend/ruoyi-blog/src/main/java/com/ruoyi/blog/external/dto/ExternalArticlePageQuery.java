package com.ruoyi.blog.external.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExternalArticlePageQuery
{
    @Min(value = 1, message = "pageNum must be at least 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize must be at least 1")
    @Max(value = 100, message = "pageSize must not exceed 100")
    private Integer pageSize = 20;

    @Size(max = 100, message = "keyword must not exceed 100 characters")
    private String keyword;
}
