package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_learning_record")
public class HistoryLearningRecord
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long pathId;
    private Long unitId;
    private Integer progress;
    private Boolean completed;
    private LocalDateTime lastStudyTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
