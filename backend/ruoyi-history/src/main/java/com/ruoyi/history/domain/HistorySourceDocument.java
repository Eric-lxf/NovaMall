package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_source_document")
public class HistorySourceDocument
{
    @TableId(type = IdType.AUTO)
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
}
