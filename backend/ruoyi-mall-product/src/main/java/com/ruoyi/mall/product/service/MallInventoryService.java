package com.ruoyi.mall.product.service;

import com.ruoyi.mall.product.domain.MallSku;
import com.ruoyi.mall.product.dto.MallInventoryAdjustRequest;

public interface MallInventoryService
{
    MallSku adjust(MallInventoryAdjustRequest request);
}
