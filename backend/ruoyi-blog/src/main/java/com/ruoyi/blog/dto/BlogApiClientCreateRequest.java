package com.ruoyi.blog.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BlogApiClientCreateRequest
{
    @NotBlank(message = "客户端名称不能为空")
    @Size(min = 2, max = 100, message = "客户端名称长度必须为2到100")
    private String clientName;

    @Valid
    @NotEmpty(message = "至少需要配置一个权限范围")
    @Size(max = 32, message = "权限范围不能超过32个")
    private List<
            @NotBlank(message = "权限范围不能为空")
            @Size(max = 100, message = "权限范围长度不能超过100")
            @Pattern(
                    regexp = "^(blog\\.article\\.create|blog\\.article\\.read\\.own|blog\\.taxonomy\\.read)$",
                    message = "权限范围不在允许列表中")
            String> scopes = List.of(
                    "blog.article.create",
                    "blog.article.read.own",
                    "blog.taxonomy.read");

    @Pattern(regexp = "[01]", message = "客户端状态不合法")
    private String status = "0";

    @Min(value = 1, message = "每分钟限流必须大于0")
    @Max(value = 100000, message = "每分钟限流不能超过100000")
    private Integer rateLimitPerMinute;

    @Min(value = 60, message = "Token有效期不能少于60秒")
    @Max(value = 3600, message = "Token有效期不能超过3600秒")
    private Integer tokenTtlSeconds;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
