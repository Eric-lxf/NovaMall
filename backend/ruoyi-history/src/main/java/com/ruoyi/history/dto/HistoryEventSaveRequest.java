package com.ruoyi.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistoryEventSaveRequest
{
    private Long id;

    @NotBlank(message = "事件标题不能为空")
    @Size(max = 200, message = "事件标题长度不能超过200")
    private String title;

    private Long periodId;
    private Long placeId;
    private Integer startYear;
    private Integer endYear;
    private String datePrecision;
    private String originalDateText;
    private String calendarType;
    private Boolean isApproximate;

    @Size(max = 2000, message = "摘要长度不能超过2000")
    private String summary;

    private String background;
    private String process;
    private String causeAnalysis;
    private String impact;

    @Size(max = 1000, message = "不确定性说明长度不能超过1000")
    private String uncertaintyNote;

    private String auditStatus;
    private String status;
    private String remark;
}
