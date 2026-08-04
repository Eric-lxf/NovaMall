package com.ruoyi.history.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HistoryExtractRequest
{
    @NotNull(message = "资料文档ID不能为空")
    private Long documentId;
}
