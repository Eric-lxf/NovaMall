package com.ruoyi.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class HnItemAdminQuery
{

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    /** news | past | show | jobs；空则不过滤快照 */
    private String board;

    private String keyword;

    /** pending | ok | fail */
    private String translateStatus;
}
