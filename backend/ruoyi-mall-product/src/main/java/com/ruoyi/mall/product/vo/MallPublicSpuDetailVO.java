package com.ruoyi.mall.product.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商城公开商品详情。库存只输出可售量，不暴露锁定量、实际库存和审计字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallPublicSpuDetailVO extends MallPublicSpuVO
{
    private String detailHtml;
    private List<SkuVO> skus;
    private List<ImageVO> images;
    private List<MallSpuAttrValueVO> attrValues;

    @Data
    public static class SkuVO
    {
        private Long id;
        private String specsJson;
        private BigDecimal price;
        private Integer stock;
        private String status;
        private List<SpecVO> specs;
    }

    @Data
    public static class SpecVO
    {
        private Long attrId;
        private Long optionId;
        private String value;
    }

    @Data
    public static class ImageVO
    {
        private String url;
        private Integer sort;
    }
}
