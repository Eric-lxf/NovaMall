package com.ruoyi.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistoryUnitSaveRequest
{
    private Long id;

    @NotBlank(message = "单元标题不能为空")
    @Size(max = 200, message = "单元标题长度不能超过200")
    private String title;

    private Long eventId;
    private Long periodId;

    @Size(max = 500, message = "一句话概括长度不能超过500")
    private String oneLiner;

    private String objectives;
    private String prerequisites;

    private String timePlace;
    private String background;
    private String keyPeople;
    private String process;
    private String causeAnalysis;
    private String impact;
    private String sourcesAndViews;
    private String practiceHint;
    private String furtherReading;

    private String auditStatus;
    private String status;
    private String remark;
}
