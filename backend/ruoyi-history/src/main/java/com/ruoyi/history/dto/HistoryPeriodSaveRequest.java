package com.ruoyi.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistoryPeriodSaveRequest
{
    private Long id;

    @NotBlank(message = "时期名称不能为空")
    @Size(max = 64, message = "时期名称长度不能超过64")
    private String name;

    @Size(max = 128, message = "别名长度不能超过128")
    private String alias;

    private Integer startYear;
    private Integer endYear;
    private String datePrecision;
    private String originalDateText;
    private String calendarType;
    private Boolean isApproximate;

    @Size(max = 1000, message = "简介长度不能超过1000")
    private String summary;

    private Integer sort;
    private String status;
    private String remark;
}
