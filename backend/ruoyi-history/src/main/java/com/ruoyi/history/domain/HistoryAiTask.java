package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_ai_task")
public class HistoryAiTask
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskType;
    private Long documentId;
    private String status;
    private String providerCode;
    private String modelName;
    private String inputPayload;
    private String outputPayload;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
