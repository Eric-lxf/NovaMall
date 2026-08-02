package com.ruoyi.mall.trade.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.mall.product.service.MallSkuStockService;
import com.ruoyi.mall.product.service.dto.MallSkuInfo;
import com.ruoyi.mall.trade.constant.MallOrderStatus;
import com.ruoyi.mall.trade.domain.MallAddress;
import com.ruoyi.mall.trade.domain.MallCart;
import com.ruoyi.mall.trade.domain.MallOrder;
import com.ruoyi.mall.trade.domain.MallOrderItem;
import com.ruoyi.mall.trade.domain.MallOrderLog;
import com.ruoyi.mall.trade.dto.AdminOrderPageQuery;
import com.ruoyi.mall.trade.dto.OrderCreateItemRequest;
import com.ruoyi.mall.trade.dto.OrderCreateRequest;
import com.ruoyi.mall.trade.dto.OrderPageQuery;
import com.ruoyi.mall.trade.mapper.MallAddressMapper;
import com.ruoyi.mall.trade.mapper.MallCartMapper;
import com.ruoyi.mall.trade.mapper.MallOrderItemMapper;
import com.ruoyi.mall.trade.mapper.MallOrderLogMapper;
import com.ruoyi.mall.trade.mapper.MallOrderMapper;
import com.ruoyi.mall.trade.service.MallOrderService;
import com.ruoyi.mall.trade.vo.MallOrderPaymentView;
import com.ruoyi.mall.trade.vo.OrderItemVO;
import com.ruoyi.mall.trade.vo.OrderLogVO;
import com.ruoyi.mall.trade.vo.OrderVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MallOrderServiceImpl implements MallOrderService
{
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal FREIGHT_FREE = BigDecimal.ZERO;
    private static final String SYSTEM_OPERATOR = "system";

    private final MallOrderMapper mallOrderMapper;
    private final MallOrderItemMapper mallOrderItemMapper;
    private final MallOrderLogMapper mallOrderLogMapper;
    private final MallAddressMapper mallAddressMapper;
    private final MallCartMapper mallCartMapper;
    private final MallSkuStockService mallSkuStockService;

    @Override
    @Transactional
    public OrderVO create(OrderCreateRequest request)
    {
        Long userId = SecurityUtils.getUserId();
        MallAddress address = requireAddress(userId, request.getAddressId());
        List<OrderCreateItemRequest> requestItems = resolveCreateItems(userId, request);
        List<OrderLine> lines = buildOrderLines(requestItems);

        BigDecimal goodsAmount = lines.stream()
                .map(line -> line.sku().getPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payAmount = goodsAmount.add(FREIGHT_FREE);

        MallOrder order = new MallOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setStatus(MallOrderStatus.PENDING_PAY);
        order.setGoodsAmount(goodsAmount);
        order.setFreightAmount(FREIGHT_FREE);
        order.setPayAmount(payAmount);
        order.setAddressSnapshot(addressSnapshot(address));
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        order.setCreateBy(SecurityUtils.getUsername());
        order.setUpdateBy(SecurityUtils.getUsername());
        mallOrderMapper.insert(order);

        String operator = SecurityUtils.getUsername();
        for (OrderLine line : lines)
        {
            if (!mallSkuStockService.lockStock(line.sku().getSkuId(), line.quantity(), order.getOrderNo(), operator))
            {
                throw new ServiceException("商品库存不足：" + line.sku().getSpuName());
            }
            mallOrderItemMapper.insert(toOrderItem(order.getId(), line));
        }
        addLog(order.getId(), null, MallOrderStatus.PENDING_PAY, "创建订单", operator);

        if (!CollectionUtils.isEmpty(request.getCartIds()))
        {
            mallCartMapper.delete(new LambdaQueryWrapper<MallCart>()
                    .eq(MallCart::getUserId, userId)
                    .in(MallCart::getId, request.getCartIds()));
        }
        return toVO(mallOrderMapper.selectById(order.getId()), true);
    }

    @Override
    public Page<OrderVO> pageMine(OrderPageQuery query)
    {
        Long userId = SecurityUtils.getUserId();
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getUserId, userId)
                .eq(StringUtils.hasText(query.getStatus()), MallOrder::getStatus, query.getStatus())
                .orderByDesc(MallOrder::getCreateTime)
                .orderByDesc(MallOrder::getId);
        Page<MallOrder> rawPage = mallOrderMapper.selectPage(new Page<>(pageNum(query.getPageNum()), pageSize(query.getPageSize())), wrapper);
        Page<OrderVO> voPage = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        voPage.setRecords(rawPage.getRecords().stream().map(order -> toVO(order, false)).toList());
        return voPage;
    }

    @Override
    public OrderVO getMine(Long id)
    {
        Long userId = SecurityUtils.getUserId();
        MallOrder order = requireOrder(id);
        if (!userId.equals(order.getUserId()))
        {
            throw new ServiceException("订单不存在");
        }
        return toVO(order, true);
    }

    @Override
    @Transactional
    public void cancelMine(Long id)
    {
        Long userId = SecurityUtils.getUserId();
        MallOrder order = requireOrder(id);
        if (!userId.equals(order.getUserId()))
        {
            throw new ServiceException("订单不存在");
        }
        cancelPendingOrder(order, "用户取消", SecurityUtils.getUsername());
    }

    @Override
    public Page<OrderVO> adminPage(AdminOrderPageQuery query)
    {
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>()
                .eq(StringUtils.hasText(query.getStatus()), MallOrder::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getOrderNo()), MallOrder::getOrderNo, query.getOrderNo())
                .ge(query.getBeginTime() != null, MallOrder::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, MallOrder::getCreateTime, query.getEndTime())
                .orderByDesc(MallOrder::getCreateTime)
                .orderByDesc(MallOrder::getId);
        Page<MallOrder> rawPage = mallOrderMapper.selectPage(new Page<>(pageNum(query.getPageNum()), pageSize(query.getPageSize())), wrapper);
        Page<OrderVO> voPage = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        voPage.setRecords(rawPage.getRecords().stream().map(order -> toVO(order, false)).toList());
        return voPage;
    }

    @Override
    public OrderVO adminDetail(Long id)
    {
        return toVO(requireOrder(id), true);
    }

    @Override
    @Transactional
    public void ship(Long id)
    {
        transition(id, MallOrderStatus.PAID, MallOrderStatus.SHIPPED, "管理员发货");
    }

    @Override
    @Transactional
    public void complete(Long id)
    {
        transition(id, MallOrderStatus.SHIPPED, MallOrderStatus.COMPLETED, "管理员完成订单");
    }

    @Override
    @Transactional
    public void cancelExpiredOrders()
    {
        List<MallOrder> orders = mallOrderMapper.selectList(new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getStatus, MallOrderStatus.PENDING_PAY)
                .lt(MallOrder::getExpireTime, LocalDateTime.now())
                .orderByAsc(MallOrder::getExpireTime)
                .last("LIMIT 200"));
        for (MallOrder order : orders)
        {
            cancelPendingOrder(order, "支付超时自动取消", SYSTEM_OPERATOR);
        }
    }

    @Override
    @Transactional
    public void markOrderPaid(Long orderId, String payNo)
    {
        MallOrder order = requireOrder(orderId);
        if (MallOrderStatus.PAID.equals(order.getStatus())
                || MallOrderStatus.SHIPPED.equals(order.getStatus())
                || MallOrderStatus.COMPLETED.equals(order.getStatus()))
        {
            return;
        }
        if (!MallOrderStatus.PENDING_PAY.equals(order.getStatus()))
        {
            throw new ServiceException("订单当前状态不允许支付");
        }
        MallOrder update = new MallOrder();
        update.setStatus(MallOrderStatus.PAID);
        update.setPayTime(LocalDateTime.now());
        update.setUpdateBy("pay:" + payNo);
        int rows = mallOrderMapper.update(update, new LambdaUpdateWrapper<MallOrder>()
                .eq(MallOrder::getId, orderId)
                .eq(MallOrder::getStatus, MallOrderStatus.PENDING_PAY));
        if (rows > 0)
        {
            List<MallOrderItem> items = mallOrderItemMapper.selectList(new LambdaQueryWrapper<MallOrderItem>()
                    .eq(MallOrderItem::getOrderId, orderId));
            for (MallOrderItem item : items)
            {
                mallSkuStockService.deductLockedStock(item.getSkuId(), item.getQuantity(), order.getOrderNo(), "payment");
            }
            addLog(orderId, MallOrderStatus.PENDING_PAY, MallOrderStatus.PAID, "支付成功：" + payNo, "payment");
            return;
        }
        MallOrder latest = requireOrder(orderId);
        if (!MallOrderStatus.PAID.equals(latest.getStatus())
                && !MallOrderStatus.SHIPPED.equals(latest.getStatus())
                && !MallOrderStatus.COMPLETED.equals(latest.getStatus()))
        {
            throw new ServiceException("订单当前状态不允许支付");
        }
    }

    @Override
    public MallOrderPaymentView getPayableOrder(Long orderId, Long userId)
    {
        MallOrder order = requireOrder(orderId);
        if (!userId.equals(order.getUserId()))
        {
            throw new ServiceException("订单不存在");
        }
        if (!MallOrderStatus.PENDING_PAY.equals(order.getStatus()))
        {
            throw new ServiceException("订单当前状态不允许支付");
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now()))
        {
            throw new ServiceException("订单已过期");
        }
        MallOrderPaymentView view = new MallOrderPaymentView();
        view.setOrderId(order.getId());
        view.setOrderNo(order.getOrderNo());
        view.setUserId(order.getUserId());
        view.setStatus(order.getStatus());
        view.setPayAmount(order.getPayAmount());
        view.setExpireTime(order.getExpireTime());
        return view;
    }

    private List<OrderCreateItemRequest> resolveCreateItems(Long userId, OrderCreateRequest request)
    {
        if (!CollectionUtils.isEmpty(request.getCartIds()))
        {
            List<MallCart> carts = mallCartMapper.selectList(new LambdaQueryWrapper<MallCart>()
                    .eq(MallCart::getUserId, userId)
                    .in(MallCart::getId, request.getCartIds()));
            if (carts.size() != request.getCartIds().stream().distinct().count())
            {
                throw new ServiceException("购物车项不存在");
            }
            return carts.stream().map(cart -> {
                OrderCreateItemRequest item = new OrderCreateItemRequest();
                item.setSkuId(cart.getSkuId());
                item.setQuantity(cart.getQuantity());
                return item;
            }).toList();
        }
        if (CollectionUtils.isEmpty(request.getItems()))
        {
            throw new ServiceException("请选择商品");
        }
        return request.getItems();
    }

    private List<OrderLine> buildOrderLines(List<OrderCreateItemRequest> requestItems)
    {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderCreateItemRequest item : requestItems)
        {
            if (item.getSkuId() == null || item.getQuantity() == null || item.getQuantity() <= 0)
            {
                throw new ServiceException("商品数量不正确");
            }
            quantities.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        List<OrderLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet())
        {
            MallSkuInfo sku = mallSkuStockService.getEnabledSku(entry.getKey());
            if (sku == null)
            {
                throw new ServiceException("商品不存在或已下架");
            }
            lines.add(new OrderLine(sku, entry.getValue()));
        }
        return lines;
    }

    private MallAddress requireAddress(Long userId, Long addressId)
    {
        MallAddress address = mallAddressMapper.selectOne(new LambdaQueryWrapper<MallAddress>()
                .eq(MallAddress::getId, addressId)
                .eq(MallAddress::getUserId, userId));
        if (address == null)
        {
            throw new ServiceException("收货地址不存在");
        }
        return address;
    }

    private MallOrder requireOrder(Long id)
    {
        MallOrder order = mallOrderMapper.selectById(id);
        if (order == null)
        {
            throw new ServiceException("订单不存在");
        }
        return order;
    }

    private void cancelPendingOrder(MallOrder order, String reason, String operator)
    {
        if (!MallOrderStatus.PENDING_PAY.equals(order.getStatus()))
        {
            throw new ServiceException("订单当前状态不允许取消");
        }
        MallOrder update = new MallOrder();
        update.setStatus(MallOrderStatus.CANCELLED);
        update.setCancelTime(LocalDateTime.now());
        update.setCancelReason(reason);
        update.setUpdateBy(operator);
        int rows = mallOrderMapper.update(update, new LambdaUpdateWrapper<MallOrder>()
                .eq(MallOrder::getId, order.getId())
                .eq(MallOrder::getStatus, MallOrderStatus.PENDING_PAY));
        if (rows == 0)
        {
            return;
        }
        List<MallOrderItem> items = mallOrderItemMapper.selectList(new LambdaQueryWrapper<MallOrderItem>()
                .eq(MallOrderItem::getOrderId, order.getId()));
        for (MallOrderItem item : items)
        {
            mallSkuStockService.unlockStock(item.getSkuId(), item.getQuantity(), order.getOrderNo(), operator);
        }
        addLog(order.getId(), MallOrderStatus.PENDING_PAY, MallOrderStatus.CANCELLED, reason, operator);
    }

    private void transition(Long id, String fromStatus, String toStatus, String remark)
    {
        requireOrder(id);
        MallOrder update = new MallOrder();
        update.setStatus(toStatus);
        update.setUpdateBy(SecurityUtils.getUsername());
        if (MallOrderStatus.SHIPPED.equals(toStatus))
        {
            update.setShipTime(LocalDateTime.now());
        }
        if (MallOrderStatus.COMPLETED.equals(toStatus))
        {
            update.setCompleteTime(LocalDateTime.now());
        }
        int rows = mallOrderMapper.update(update, new LambdaUpdateWrapper<MallOrder>()
                .eq(MallOrder::getId, id)
                .eq(MallOrder::getStatus, fromStatus));
        if (rows == 0)
        {
            throw new ServiceException("订单当前状态不允许操作");
        }
        addLog(id, fromStatus, toStatus, remark, SecurityUtils.getUsername());
    }

    private MallOrderItem toOrderItem(Long orderId, OrderLine line)
    {
        MallSkuInfo sku = line.sku();
        MallOrderItem item = new MallOrderItem();
        item.setOrderId(orderId);
        item.setSpuId(sku.getSpuId());
        item.setSkuId(sku.getSkuId());
        item.setSpuName(sku.getSpuName());
        item.setSkuSpecs(sku.getSkuSpecs());
        item.setSkuCode(sku.getSkuCode());
        item.setImage(sku.getImage());
        item.setPrice(sku.getPrice());
        item.setQuantity(line.quantity());
        return item;
    }

    private void addLog(Long orderId, String fromStatus, String toStatus, String remark, String operator)
    {
        MallOrderLog log = new MallOrderLog();
        log.setOrderId(orderId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setRemark(remark);
        log.setCreateBy(operator);
        mallOrderLogMapper.insert(log);
    }

    private OrderVO toVO(MallOrder order, boolean includeLogs)
    {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        List<MallOrderItem> items = mallOrderItemMapper.selectList(new LambdaQueryWrapper<MallOrderItem>()
                .eq(MallOrderItem::getOrderId, order.getId())
                .orderByAsc(MallOrderItem::getId));
        vo.setItems(items.stream().map(this::toItemVO).toList());
        if (includeLogs)
        {
            List<MallOrderLog> logs = mallOrderLogMapper.selectList(new LambdaQueryWrapper<MallOrderLog>()
                    .eq(MallOrderLog::getOrderId, order.getId())
                    .orderByAsc(MallOrderLog::getId));
            vo.setLogs(logs.stream().map(this::toLogVO).toList());
        }
        return vo;
    }

    private OrderItemVO toItemVO(MallOrderItem item)
    {
        OrderItemVO vo = new OrderItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    private OrderLogVO toLogVO(MallOrderLog log)
    {
        OrderLogVO vo = new OrderLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    private String addressSnapshot(MallAddress address)
    {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("receiver", address.getReceiver());
        snapshot.put("mobile", address.getMobile());
        snapshot.put("province", address.getProvince());
        snapshot.put("city", address.getCity());
        snapshot.put("district", address.getDistrict());
        snapshot.put("detail", address.getDetail());
        return JSON.toJSONString(snapshot);
    }

    private String generateOrderNo()
    {
        return LocalDateTime.now().format(ORDER_NO_TIME)
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }

    private int pageNum(Integer pageNum)
    {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int pageSize(Integer pageSize)
    {
        return pageSize == null ? 10 : Math.min(pageSize, 100);
    }

    private record OrderLine(MallSkuInfo sku, Integer quantity)
    {
    }
}
