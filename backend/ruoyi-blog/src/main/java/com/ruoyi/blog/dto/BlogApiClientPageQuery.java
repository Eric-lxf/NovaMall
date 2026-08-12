package com.ruoyi.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BlogApiClientPageQuery
{
    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    /** 按客户端名称或 clientId 模糊查询。 */
    private String keyword;

    /** RuoYi 状态：0 启用 / 1 停用。 */
    @Pattern(regexp = "[01]", message = "客户端状态不合法")
    private String status;
}
