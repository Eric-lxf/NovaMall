package com.ruoyi.mall.product.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.mall.product.constant.MallInventoryChangeType;
import com.ruoyi.mall.product.constant.MallProductConstants;
import com.ruoyi.mall.product.domain.MallInventoryLog;
import com.ruoyi.mall.product.domain.MallSku;
import com.ruoyi.mall.product.domain.MallSpu;
import com.ruoyi.mall.product.mapper.MallInventoryLogMapper;
import com.ruoyi.mall.product.mapper.MallSkuMapper;
import com.ruoyi.mall.product.mapper.MallSpuMapper;
import com.ruoyi.mall.product.service.MallSkuStockService;
import com.ruoyi.mall.product.service.dto.MallSkuInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MallSkuStockServiceImpl implements MallSkuStockService
{
    private final MallSkuMapper mallSkuMapper;
    private final MallSpuMapper mallSpuMapper;
    private final MallInventoryLogMapper mallInventoryLogMapper;

    @Override
    @Transactional
    public boolean lockStock(Long skuId, int qty, String bizId, String operator)
    {
        validateQty(qty);
        if (alreadyLogged(skuId, MallInventoryChangeType.ORDER_LOCK, bizId))
        {
            return true;
        }
        MallSku before = requireSku(skuId);
        if (mallSkuMapper.lockStock(skuId, qty) == 0)
        {
            return false;
        }
        MallSku after = requireSku(skuId);
        writeLog(before, after, MallInventoryChangeType.ORDER_LOCK, qty,
                MallInventoryChangeType.BIZ_TYPE_ORDER, bizId, operator, "下单锁定库存");
        return true;
    }

    @Override
    @Transactional
    public void unlockStock(Long skuId, int qty, String bizId, String operator)
    {
        validateQty(qty);
        if (alreadyLogged(skuId, MallInventoryChangeType.ORDER_UNLOCK, bizId)
                || alreadyLogged(skuId, MallInventoryChangeType.ORDER_CANCEL, bizId))
        {
            return;
        }
        MallSku before = requireSku(skuId);
        if (mallSkuMapper.unlockStock(skuId, qty) == 0)
        {
            throw new ServiceException("SKU锁定库存不足，解锁失败", HttpStatus.BAD_REQUEST);
        }
        MallSku after = requireSku(skuId);
        writeLog(before, after, MallInventoryChangeType.ORDER_UNLOCK, qty,
                MallInventoryChangeType.BIZ_TYPE_ORDER, bizId, operator, "订单取消解锁库存");
    }

    @Override
    @Transactional
    public void deductLockedStock(Long skuId, int qty, String bizId, String operator)
    {
        validateQty(qty);
        if (alreadyLogged(skuId, MallInventoryChangeType.ORDER_DEDUCT, bizId))
        {
            return;
        }
        MallSku before = requireSku(skuId);
        if (mallSkuMapper.deductLockedStock(skuId, qty) == 0)
        {
            throw new ServiceException("SKU锁定库存不足，支付扣减失败", HttpStatus.BAD_REQUEST);
        }
        MallSku after = requireSku(skuId);
        writeLog(before, after, MallInventoryChangeType.ORDER_DEDUCT, qty,
                MallInventoryChangeType.BIZ_TYPE_ORDER, bizId, operator, "支付成功扣减库存");
    }

    @Override
    public MallSkuInfo getEnabledSku(Long skuId)
    {
        MallSku sku = mallSkuMapper.selectOne(new LambdaQueryWrapper<MallSku>()
                .eq(MallSku::getId, skuId)
                .eq(MallSku::getStatus, MallProductConstants.STATUS_NORMAL)
                .eq(MallSku::getDelFlag, MallProductConstants.DEL_FLAG_NORMAL));
        if (sku == null)
        {
            return null;
        }
        MallSpu spu = mallSpuMapper.selectOne(new LambdaQueryWrapper<MallSpu>()
                .eq(MallSpu::getId, sku.getSpuId())
                .eq(MallSpu::getStatus, MallProductConstants.SPU_STATUS_ON)
                .eq(MallSpu::getDelFlag, MallProductConstants.DEL_FLAG_NORMAL));
        if (spu == null)
        {
            return null;
        }
        MallSkuInfo info = new MallSkuInfo();
        info.setSkuId(sku.getId());
        info.setSpuId(sku.getSpuId());
        info.setSpuName(spu.getName());
        info.setSkuCode(sku.getSkuCode());
        info.setSkuSpecs(sku.getSpecsJson());
        info.setImage(spu.getMainImage());
        info.setPrice(sku.getPrice());
        Integer available = sku.getStockAvailable() != null ? sku.getStockAvailable() : sku.getStock();
        info.setStock(available == null ? 0 : available);
        return info;
    }

    private boolean alreadyLogged(Long skuId, String changeType, String bizId)
    {
        if (!StringUtils.hasText(bizId))
        {
            return false;
        }
        Long count = mallInventoryLogMapper.selectCount(new LambdaQueryWrapper<MallInventoryLog>()
                .eq(MallInventoryLog::getSkuId, skuId)
                .eq(MallInventoryLog::getChangeType, changeType)
                .eq(MallInventoryLog::getBizType, MallInventoryChangeType.BIZ_TYPE_ORDER)
                .eq(MallInventoryLog::getBizId, bizId));
        return count != null && count > 0;
    }

    private MallSku requireSku(Long skuId)
    {
        MallSku sku = mallSkuMapper.selectById(skuId);
        if (sku == null || !MallProductConstants.DEL_FLAG_NORMAL.equals(sku.getDelFlag()))
        {
            throw new ServiceException("SKU不存在", HttpStatus.NOT_FOUND);
        }
        return sku;
    }

    private void writeLog(MallSku before, MallSku after, String changeType, int qty,
            String bizType, String bizId, String operator, String remark)
    {
        MallInventoryLog log = new MallInventoryLog();
        log.setSkuId(before.getId());
        log.setChangeType(changeType);
        log.setQuantity(qty);
        log.setBeforeTotal(nz(before.getStockTotal()));
        log.setAfterTotal(nz(after.getStockTotal()));
        log.setBeforeLocked(nz(before.getStockLocked()));
        log.setAfterLocked(nz(after.getStockLocked()));
        log.setBeforeAvailable(nz(before.getStockAvailable()));
        log.setAfterAvailable(nz(after.getStockAvailable()));
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setOperator(operator == null ? "" : operator);
        log.setRemark(remark);
        mallInventoryLogMapper.insert(log);
    }

    private int nz(Integer value)
    {
        return value == null ? 0 : value;
    }

    private void validateQty(int qty)
    {
        if (qty <= 0)
        {
            throw new ServiceException("库存数量必须大于0", HttpStatus.BAD_REQUEST);
        }
    }
}
