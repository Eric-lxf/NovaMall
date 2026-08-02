package com.ruoyi.mall.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.mall.product.domain.MallSku;

@Mapper
public interface MallSkuMapper extends BaseMapper<MallSku>
{
    /** 下单锁定：可售减少、锁定增加；兼容旧 stock 字段 */
    @Update("UPDATE mall_sku SET stock_available = stock_available - #{qty}, stock_locked = stock_locked + #{qty}, "
            + "stock = stock - #{qty}, update_time = NOW() "
            + "WHERE id = #{skuId} AND stock_available >= #{qty} AND status = '0' AND del_flag = '0'")
    int lockStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /** 取消解锁：锁定减少、可售回补 */
    @Update("UPDATE mall_sku SET stock_locked = stock_locked - #{qty}, stock_available = stock_available + #{qty}, "
            + "stock = stock + #{qty}, update_time = NOW() "
            + "WHERE id = #{skuId} AND stock_locked >= #{qty} AND del_flag = '0'")
    int unlockStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /** 支付扣减：锁定与实际库存同时减少 */
    @Update("UPDATE mall_sku SET stock_locked = stock_locked - #{qty}, stock_total = stock_total - #{qty}, "
            + "update_time = NOW() "
            + "WHERE id = #{skuId} AND stock_locked >= #{qty} AND stock_total >= #{qty} AND del_flag = '0'")
    int deductLockedStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /** 手工入库/回补：实际与可售增加 */
    @Update("UPDATE mall_sku SET stock_total = stock_total + #{qty}, stock_available = stock_available + #{qty}, "
            + "stock = stock + #{qty}, update_time = NOW() "
            + "WHERE id = #{skuId} AND del_flag = '0'")
    int increaseStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /** 手工出库/报损：实际与可售减少 */
    @Update("UPDATE mall_sku SET stock_total = stock_total - #{qty}, stock_available = stock_available - #{qty}, "
            + "stock = stock - #{qty}, update_time = NOW() "
            + "WHERE id = #{skuId} AND stock_available >= #{qty} AND stock_total >= #{qty} AND del_flag = '0'")
    int decreaseStock(@Param("skuId") Long skuId, @Param("qty") int qty);
}
