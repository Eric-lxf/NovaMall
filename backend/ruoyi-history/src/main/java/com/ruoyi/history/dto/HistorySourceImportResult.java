package com.ruoyi.history.dto;

import lombok.Data;

@Data
public class HistorySourceImportResult
{
    private Long documentId;
    private Long taskId;
    private String status;
}
