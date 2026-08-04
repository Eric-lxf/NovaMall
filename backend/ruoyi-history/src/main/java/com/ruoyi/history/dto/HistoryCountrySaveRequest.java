package com.ruoyi.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistoryCountrySaveRequest
{
    private Long id;

    @NotBlank(message = "国家名称不能为空")
    @Size(max = 64, message = "国家名称长度不能超过64")
    private String name;

    @Size(max = 128, message = "别名长度不能超过128")
    private String alias;

    @Size(max = 64, message = "区域长度不能超过64")
    private String region;

    @Size(max = 32, message = "时期称呼长度不能超过32")
    private String periodLabel;

    @Size(max = 1000, message = "简介长度不能超过1000")
    private String summary;

    private Integer sort;
    private String status;
    private String remark;
}
