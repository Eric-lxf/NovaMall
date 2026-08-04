package com.ruoyi.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistoryPersonSaveRequest
{
    private Long id;

    @NotBlank(message = "人物姓名不能为空")
    @Size(max = 64, message = "人物姓名长度不能超过64")
    private String name;

    @Size(max = 255, message = "别名长度不能超过255")
    private String alias;

    private Long periodId;
    private Integer birthYear;
    private Integer deathYear;
    private String datePrecision;
    private String originalDateText;
    private String calendarType;
    private Boolean isApproximate;
    private Long birthPlaceId;

    @Size(max = 2000, message = "简介长度不能超过2000")
    private String summary;

    @Size(max = 1000, message = "不确定性说明长度不能超过1000")
    private String uncertaintyNote;

    private String auditStatus;
    private String status;
    private String remark;
}
