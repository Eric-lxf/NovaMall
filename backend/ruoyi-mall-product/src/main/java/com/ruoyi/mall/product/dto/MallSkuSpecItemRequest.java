package com.ruoyi.mall.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MallSkuSpecItemRequest
{
    @NotNull(message = "规格属性ID不能为空")
    private Long attrId;

    private Long optionId;

    private String value;
}
