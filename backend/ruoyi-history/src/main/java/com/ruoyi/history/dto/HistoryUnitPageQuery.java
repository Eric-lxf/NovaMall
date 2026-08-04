package com.ruoyi.history.dto;

import lombok.Data;

@Data
public class HistoryUnitPageQuery
{
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String title;
    private Long periodId;
    private Long eventId;
    private String auditStatus;
    private String status;
}
