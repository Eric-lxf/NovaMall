package com.ruoyi.mall.product.service.impl;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.mall.product.constant.MallInventoryChangeType;
import com.ruoyi.mall.product.constant.MallProductConstants;
import com.ruoyi.mall.product.domain.MallInventoryLog;
import com.ruoyi.mall.product.domain.MallSku;
import com.ruoyi.mall.product.dto.MallInventoryAdjustRequest;
import com.ruoyi.mall.product.mapper.MallInventoryLogMapper;
import com.ruoyi.mall.product.mapper.MallSkuMapper;
import com.ruoyi.mall.product.service.MallInventoryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MallInventoryServiceImpl implements MallInventoryService
{
    private static final Set<String> IN_REASONS = Set.of(
            MallInventoryChangeType.PURCHASE_IN,
            MallInventoryChangeType.MANUAL_IN,
            MallInventoryChangeType.RETURN,
            MallInventoryChangeType.CORRECTION,
            MallInventoryChangeType.REFUND_RETURN);

    private static final Set<String> OUT_REASONS = Set.of(
            MallInventoryChangeType.MANUAL_OUT,
            MallInventoryChangeType.DAMAGE);

    private final MallSkuMapper mallSkuMapper;
    private final MallInventoryLogMapper mallInventoryLogMapper;

    @Override
    @Transactional
    public MallSku adjust(MallInventoryAdjustRequest request)
    {
        if (request.getQuantity() == null || request.getQuantity() == 0)
        {
            throw new ServiceException("调整数量不能为0", HttpStatus.BAD_REQUEST);
        }
        long signedQuantity = request.getQuantity().longValue();
        long absoluteQuantity = Math.abs(signedQuantity);
        if (absoluteQuantity > MallProductConstants.MAX_INVENTORY_CHANGE_QUANTITY)
        {
            throw new ServiceException("单次库存调整不能超过" + MallProductConstants.MAX_INVENTORY_CHANGE_QUANTITY,
                    HttpStatus.BAD_REQUEST);
        }
        String reason = request.getReason().trim().toUpperCase(Locale.ROOT);
        MallSku before = requireSku(request.getSkuId());
        int absQty = (int) absoluteQuantity;
        boolean inbound = signedQuantity > 0;

        if (inbound)
        {
            if (!IN_REASONS.contains(reason) && !MallInventoryChangeType.MANUAL_ADJUST.equals(reason))
            {
                throw new ServiceException("入库原因不合法", HttpStatus.BAD_REQUEST);
            }
            if (mallSkuMapper.increaseStock(request.getSkuId(), absQty) == 0)
            {
                throw new ServiceException("库存调整失败", HttpStatus.BAD_REQUEST);
            }
        }
        else
        {
            if (!OUT_REASONS.contains(reason) && !MallInventoryChangeType.MANUAL_ADJUST.equals(reason)
                    && !MallInventoryChangeType.CORRECTION.equals(reason))
            {
                throw new ServiceException("出库原因不合法", HttpStatus.BAD_REQUEST);
            }
            if (mallSkuMapper.decreaseStock(request.getSkuId(), absQty) == 0)
            {
                throw new ServiceException("可售库存不足，无法出库", HttpStatus.BAD_REQUEST);
            }
        }

        MallSku after = requireSku(request.getSkuId());
        MallInventoryLog log = new MallInventoryLog();
        log.setSkuId(after.getId());
        log.setChangeType(resolveChangeType(reason, inbound));
        log.setQuantity(absQty);
        log.setBeforeTotal(nz(before.getStockTotal()));
        log.setAfterTotal(nz(after.getStockTotal()));
        log.setBeforeLocked(nz(before.getStockLocked()));
        log.setAfterLocked(nz(after.getStockLocked()));
        log.setBeforeAvailable(nz(before.getStockAvailable()));
        log.setAfterAvailable(nz(after.getStockAvailable()));
        log.setBizType(MallInventoryChangeType.BIZ_TYPE_ADJUST);
        log.setBizId(String.valueOf(System.currentTimeMillis()));
        log.setOperator(SecurityUtils.getUsername());
        log.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark() : reason);
        mallInventoryLogMapper.insert(log);
        return after;
    }

    private String resolveChangeType(String reason, boolean inbound)
    {
        if (MallInventoryChangeType.MANUAL_ADJUST.equals(reason))
        {
            return MallInventoryChangeType.MANUAL_ADJUST;
        }
        if (IN_REASONS.contains(reason) || OUT_REASONS.contains(reason))
        {
            return reason;
        }
        return inbound ? MallInventoryChangeType.MANUAL_IN : MallInventoryChangeType.MANUAL_OUT;
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

    private int nz(Integer value)
    {
        return value == null ? 0 : value;
    }
}
