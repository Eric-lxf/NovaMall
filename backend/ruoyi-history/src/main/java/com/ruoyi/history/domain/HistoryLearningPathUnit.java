package com.ruoyi.history.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_learning_path_unit")
public class HistoryLearningPathUnit
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pathId;
    private Long unitId;
    private Integer sort;
}
