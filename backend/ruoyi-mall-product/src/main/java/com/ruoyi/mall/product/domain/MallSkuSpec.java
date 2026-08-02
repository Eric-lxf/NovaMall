package com.ruoyi.mall.product.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("mall_sku_spec")
public class MallSkuSpec
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private Long attrId;
    private Long optionId;
    private String value;
    private LocalDateTime createTime;
}
