package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_event_relation")
public class HistoryEventRelation
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromEventId;
    private Long toEventId;
    private String relationType;
    private String description;
    private String auditStatus;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
