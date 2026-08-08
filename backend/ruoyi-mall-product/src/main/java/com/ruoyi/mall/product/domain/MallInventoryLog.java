package com.ruoyi.mall.product.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("mall_inventory_log")
public class MallInventoryLog
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private String changeType;
    private Integer quantity;
    private Integer beforeTotal;
    private Integer afterTotal;
    private Integer beforeLocked;
    private Integer afterLocked;
    private Integer beforeAvailable;
    private Integer afterAvailable;
    private String bizType;
    private String bizId;
    private String idempotencyKey;
    private String operator;
    private String remark;
    private LocalDateTime createTime;
}
