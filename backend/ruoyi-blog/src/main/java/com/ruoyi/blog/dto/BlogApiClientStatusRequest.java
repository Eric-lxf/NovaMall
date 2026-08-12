package com.ruoyi.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BlogApiClientStatusRequest
{
    @NotBlank(message = "客户端状态不能为空")
    @Pattern(regexp = "[01]", message = "客户端状态不合法")
    private String status;
}
