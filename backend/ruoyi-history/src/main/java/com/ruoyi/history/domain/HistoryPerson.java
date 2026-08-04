package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_person")
public class HistoryPerson
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String alias;
    private Long periodId;
    private Integer birthYear;
    private Integer deathYear;
    private String datePrecision;
    private String originalDateText;
    private String calendarType;
    private Boolean isApproximate;
    private Long birthPlaceId;
    private String summary;
    private String uncertaintyNote;
    private String auditStatus;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}
