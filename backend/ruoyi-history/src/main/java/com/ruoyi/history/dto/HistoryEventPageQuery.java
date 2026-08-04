package com.ruoyi.history.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class HistoryEventPageQuery
{
    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    private String title;
    private Long periodId;
    private String auditStatus;
    private String status;
    private Integer yearFrom;
    private Integer yearTo;
}
