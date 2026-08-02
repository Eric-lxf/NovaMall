package com.ruoyi.mall.product.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("mall_sku")
public class MallSku
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spuId;
    private String skuCode;
    private String specsJson;
    /** 规范化规格键：attr:10=option:101|attr:11=option:203 */
    private String specKey;
    private BigDecimal price;
    /** 兼容旧字段，与 stockAvailable 双写 */
    private Integer stock;
    private Integer stockTotal;
    private Integer stockLocked;
    private Integer stockAvailable;
    private Integer stockWarning;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
    private String delFlag;

    @TableField(exist = false)
    private List<MallSkuSpec> specs = new ArrayList<>();
}
