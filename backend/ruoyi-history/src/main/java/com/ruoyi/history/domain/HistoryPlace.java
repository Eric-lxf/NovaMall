package com.ruoyi.history.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("history_place")
public class HistoryPlace
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String alias;
    private String modernName;
    private String region;
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;
    private String summary;
    private String auditStatus;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}
