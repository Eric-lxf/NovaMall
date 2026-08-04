package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_event")
public class HistoryEvent
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Long periodId;
    private Long placeId;
    private Integer startYear;
    private Integer endYear;
    private String datePrecision;
    private String originalDateText;
    private String calendarType;
    private Boolean isApproximate;
    private String summary;
    private String background;
    private String process;
    private String causeAnalysis;
    private String impact;
    private String uncertaintyNote;
    private String auditStatus;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}
