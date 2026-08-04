package com.ruoyi.history.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class HistoryTimelineQuery
{
    private Long periodId;
    private Integer yearFrom;
    private Integer yearTo;

    @Min(1)
    @Max(500)
    private Integer limit = 100;
}
