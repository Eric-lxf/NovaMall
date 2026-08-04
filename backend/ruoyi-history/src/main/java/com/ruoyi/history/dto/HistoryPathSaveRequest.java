package com.ruoyi.history.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistoryPathSaveRequest
{
    private Long id;

    @NotBlank(message = "路径标题不能为空")
    @Size(max = 200, message = "路径标题长度不能超过200")
    private String title;

    @Size(max = 1000, message = "简介长度不能超过1000")
    private String summary;

    private Long periodId;
    private String difficulty;
    private Integer estimatedDays;

    /** 单元 ID 列表，顺序即学习顺序 */
    private List<Long> unitIds;

    private String auditStatus;
    private String status;
    private String remark;
}
