package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_source_fragment")
public class HistorySourceFragment
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer seqNo;
    private Integer pageNo;
    private String locator;
    private String content;
    private LocalDateTime createTime;
}
