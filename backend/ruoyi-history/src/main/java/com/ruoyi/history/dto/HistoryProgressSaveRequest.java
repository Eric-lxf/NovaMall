package com.ruoyi.history.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HistoryProgressSaveRequest
{
    private Long pathId;

    @NotNull(message = "单元ID不能为空")
    private Long unitId;

    @Min(value = 0, message = "进度不能小于0")
    @Max(value = 100, message = "进度不能大于100")
    private Integer progress;

    private Boolean completed;
}
