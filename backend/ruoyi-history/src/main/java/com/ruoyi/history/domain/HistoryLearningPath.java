package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_learning_path")
public class HistoryLearningPath
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String summary;
    private Long periodId;
    private String difficulty;
    private Integer estimatedDays;
    private String auditStatus;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}
