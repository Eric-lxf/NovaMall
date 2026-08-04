package com.ruoyi.history.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class HistoryUnitVO
{
    private Long id;
    private String title;
    private Long eventId;
    private Long periodId;
    private String oneLiner;
    private String objectives;
    private String prerequisites;
    private HistoryUnitContentVO content;
    private String auditStatus;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 路径内排序（仅路径详情返回） */
    private Integer sort;
    /** 当前用户进度 0-100 */
    private Integer progress;
    private Boolean completed;
}
