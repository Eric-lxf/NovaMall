package com.ruoyi.history.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistoryPlaceSaveRequest
{
    private Long id;

    @NotBlank(message = "地点名称不能为空")
    @Size(max = 128, message = "地点名称长度不能超过128")
    private String name;

    @Size(max = 255, message = "别名长度不能超过255")
    private String alias;

    @Size(max = 128, message = "今地名长度不能超过128")
    private String modernName;

    @Size(max = 128, message = "区域长度不能超过128")
    private String region;

    private BigDecimal longitude;
    private BigDecimal latitude;

    @Size(max = 1000, message = "简介长度不能超过1000")
    private String summary;

    private String auditStatus;
    private String status;
    private String remark;
}
