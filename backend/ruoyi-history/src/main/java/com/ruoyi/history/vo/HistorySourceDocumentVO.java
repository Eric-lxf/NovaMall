package com.ruoyi.history.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.ruoyi.history.domain.HistorySourceFragment;

import lombok.Data;

@Data
public class HistorySourceDocumentVO
{
    private Long id;
    private String title;
    private String fileType;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String contentText;
    private String sourceDesc;
    private String parseStatus;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
    private Integer fragmentCount;
    private List<HistorySourceFragment> fragments;
}
