package com.ruoyi.blog.external.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 外部客户端创建草稿的专用 DTO；状态、作者和内部字段均由服务端决定。 */
@Data
public class ExternalArticleCreateRequest
{
    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title must not exceed 255 characters")
    private String title;

    @Size(max = 500, message = "summary must not exceed 500 characters")
    private String summary;

    @NotBlank(message = "contentMarkdown is required")
    private String contentMarkdown;

    @Size(max = 255, message = "coverImage must not exceed 255 characters")
    private String coverImage;

    @Positive(message = "categoryId must be positive")
    private Long categoryId;

    @Size(max = 10, message = "tagIds must contain at most 10 items")
    private List<@Positive(message = "tagId must be positive") Long> tagIds;

    @Size(max = 128, message = "externalId must not exceed 128 characters")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}", message = "externalId has an invalid format")
    private String externalId;
}
