package com.ruoyi.history.dto;

import lombok.Data;

@Data
public class HistoryPathPageQuery
{
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String title;
    private Long periodId;
    private String auditStatus;
    private String status;
}
