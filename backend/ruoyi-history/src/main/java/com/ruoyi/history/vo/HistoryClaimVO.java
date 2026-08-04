package com.ruoyi.history.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class HistoryClaimVO
{
    private Long id;
    private String claimType;
    private String targetType;
    private Long targetId;
    private Long fragmentId;
    private Integer fragmentSeqNo;
    private String fragmentLocator;
    private String fragmentPreview;
    private Long documentId;
    private String documentTitle;
    private String claimText;
    private BigDecimal confidence;
    private String uncertaintyNote;
    private String auditStatus;
    private Long taskId;
    private String createBy;
    private LocalDateTime createTime;
}
