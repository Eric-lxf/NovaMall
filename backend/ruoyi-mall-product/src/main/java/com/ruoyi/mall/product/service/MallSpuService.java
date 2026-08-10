package com.ruoyi.mall.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.mall.product.dto.MallSpuPageQuery;
import com.ruoyi.mall.product.dto.MallSpuSaveRequest;
import com.ruoyi.mall.product.vo.MallPublicSpuDetailVO;
import com.ruoyi.mall.product.vo.MallPublicSpuVO;
import com.ruoyi.mall.product.vo.MallSpuDetailVO;
import com.ruoyi.mall.product.vo.MallSpuVO;

public interface MallSpuService
{
    Page<MallSpuVO> page(MallSpuPageQuery query);

    Page<MallPublicSpuVO> publicPage(MallSpuPageQuery query);

    MallSpuDetailVO detail(Long id);

    MallPublicSpuDetailVO publicDetail(Long id);

    Long save(MallSpuSaveRequest request);

    void delete(Long id);

    void publish(Long id, String status);
}
