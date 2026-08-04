package com.ruoyi.history.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HistoryUnitGenerateRequest
{
    @NotNull(message = "事件ID不能为空")
    private Long eventId;
}
