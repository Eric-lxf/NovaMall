package com.ruoyi.mall.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MallInventoryAdjustRequest
{
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    /** 正数入库，负数出库 */
    @NotNull(message = "调整数量不能为空")
    private Integer quantity;

    @NotBlank(message = "调整原因不能为空")
    @Size(max = 32, message = "调整原因长度不能超过32")
    private String reason;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
