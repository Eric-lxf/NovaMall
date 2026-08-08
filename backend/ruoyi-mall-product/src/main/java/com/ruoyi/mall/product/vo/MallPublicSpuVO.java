package com.ruoyi.mall.product.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 商城公开商品摘要，仅暴露 C 端展示所需字段。
 */
@Data
public class MallPublicSpuVO
{
    private Long id;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private String name;
    private String subtitle;
    private String mainImage;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
