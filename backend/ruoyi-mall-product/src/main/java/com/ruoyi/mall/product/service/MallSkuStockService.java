package com.ruoyi.mall.product.service;

import java.util.UUID;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.mall.product.service.dto.MallSkuInfo;

public interface MallSkuStockService
{
    /** 下单锁定可售库存 */
    boolean lockStock(Long skuId, int qty, String bizId, String operator);

    /** 取消订单解锁；同一 bizId 幂等 */
    void unlockStock(Long skuId, int qty, String bizId, String operator);

    /** 支付成功扣减锁定库存；同一 bizId 幂等 */
    void deductLockedStock(Long skuId, int qty, String bizId, String operator);

    /** @deprecated 请改用 lockStock；保留兼容旧调用 */
    @Deprecated
    default boolean deductStock(Long skuId, int qty)
    {
        return lockStock(skuId, qty, legacyBizId(), "system");
    }

    @Deprecated
    default boolean deductStock(Long skuId, Integer qty)
    {
        if (qty == null)
        {
            throw new ServiceException("商品数量不正确");
        }
        return deductStock(skuId, qty.intValue());
    }

    /** @deprecated 请改用 unlockStock */
    @Deprecated
    default void restoreStock(Long skuId, int qty)
    {
        unlockStock(skuId, qty, legacyBizId(), "system");
    }

    @Deprecated
    default void restoreStock(Long skuId, Integer qty)
    {
        if (qty == null)
        {
            throw new ServiceException("商品数量不正确");
        }
        restoreStock(skuId, qty.intValue());
    }

    MallSkuInfo getEnabledSku(Long skuId);

    private static String legacyBizId()
    {
        return "legacy-" + UUID.randomUUID();
    }
}
