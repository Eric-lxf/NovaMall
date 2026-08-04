package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_country")
public class HistoryCountry
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String alias;
    private String region;
    private String periodLabel;
    private String summary;
    private Integer sort;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}
