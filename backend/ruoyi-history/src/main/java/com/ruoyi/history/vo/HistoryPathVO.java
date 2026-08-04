package com.ruoyi.history.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class HistoryPathVO
{
    private Long id;
    private String title;
    private String summary;
    private Long periodId;
    private String difficulty;
    private Integer estimatedDays;
    private String auditStatus;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<Long> unitIds;
    private List<HistoryUnitVO> units;
    /** 路径整体进度 0-100（登录用户） */
    private Integer pathProgress;
    private Integer completedUnitCount;
    private Integer totalUnitCount;
}
