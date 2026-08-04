package com.ruoyi.history.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_knowledge_claim")
public class HistoryKnowledgeClaim
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String claimType;
    private String targetType;
    private Long targetId;
    private Long fragmentId;
    private Long documentId;
    private String claimText;
    private BigDecimal confidence;
    private String uncertaintyNote;
    private String auditStatus;
    private Long taskId;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
