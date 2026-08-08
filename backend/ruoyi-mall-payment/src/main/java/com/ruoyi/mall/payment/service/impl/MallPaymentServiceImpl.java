package com.ruoyi.mall.payment.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.mall.payment.constant.MallPaymentConstants;
import com.ruoyi.mall.payment.domain.MallPaymentOrder;
import com.ruoyi.mall.payment.dto.PaymentCreateRequest;
import com.ruoyi.mall.payment.dto.PaymentPageQuery;
import com.ruoyi.mall.payment.gateway.NotifyResult;
import com.ruoyi.mall.payment.gateway.PaymentCreateCmd;
import com.ruoyi.mall.payment.gateway.PaymentCreateResult;
import com.ruoyi.mall.payment.gateway.PaymentGateway;
import com.ruoyi.mall.payment.mapper.MallPaymentOrderMapper;
import com.ruoyi.mall.payment.service.MallPaymentService;
import com.ruoyi.mall.trade.service.MallOrderService;
import com.ruoyi.mall.trade.vo.MallOrderPaymentView;

@Service
public class MallPaymentServiceImpl implements MallPaymentService
{
    private static final DateTimeFormatter PAY_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MallPaymentOrderMapper mallPaymentOrderMapper;
    private final MallOrderService mallOrderService;
    private final Map<String, PaymentGateway> gateways;

    public MallPaymentServiceImpl(MallPaymentOrderMapper mallPaymentOrderMapper, MallOrderService mallOrderService,
            List<PaymentGateway> gateways)
    {
        this.mallPaymentOrderMapper = mallPaymentOrderMapper;
        this.mallOrderService = mallOrderService;
        this.gateways = gateways.stream().collect(Collectors.toMap(PaymentGateway::channel, Function.identity()));
    }

    @Override
    @Transactional
    public PaymentCreateResult create(PaymentCreateRequest request)
    {
        Long userId = SecurityUtils.getUserId();
        String channel = normalizeChannel(request.getChannel());
        PaymentGateway gateway = requireGateway(channel);
        MallOrderPaymentView order = mallOrderService.getPayableOrder(request.getOrderId(), userId);

        MallPaymentOrder active = findActivePayment(order.getOrderId());
        if (active != null)
        {
            if (channel.equals(active.getChannel()) && !isExpired(active))
            {
                return createGatewayPayment(gateway, order, active.getPayNo());
            }
            throw new ServiceException("订单已有支付处理中，请勿重复提交");
        }

        MallPaymentOrder payment = new MallPaymentOrder();
        payment.setPayNo(generatePayNo());
        payment.setOrderId(order.getOrderId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(order.getUserId());
        payment.setChannel(channel);
        payment.setAmount(order.getPayAmount());
        payment.setStatus(MallPaymentConstants.STATUS_INIT);
        payment.setActiveFlag(1);
        payment.setExpireTime(order.getExpireTime());
        try
        {
            mallPaymentOrderMapper.insert(payment);
        }
        catch (DuplicateKeyException ex)
        {
            throw new ServiceException("订单已有支付处理中，请勿重复提交");
        }

        PaymentCreateResult result = createGatewayPayment(gateway, order, payment.getPayNo());

        MallPaymentOrder update = new MallPaymentOrder();
        update.setStatus(MallPaymentConstants.STATUS_PAYING);
        mallPaymentOrderMapper.update(update, new LambdaUpdateWrapper<MallPaymentOrder>()
                .eq(MallPaymentOrder::getId, payment.getId())
                .eq(MallPaymentOrder::getStatus, MallPaymentConstants.STATUS_INIT));
        return result;
    }

    @Override
    @Transactional
    public void confirmMock(String payNo)
    {
        requireGateway(MallPaymentConstants.CHANNEL_MOCK);
        MallPaymentOrder payment = requireByPayNo(payNo);
        if (!MallPaymentConstants.CHANNEL_MOCK.equals(payment.getChannel()))
        {
            throw new ServiceException("仅Mock支付允许手动确认");
        }
        Long userId = SecurityUtils.getUserId();
        if (!userId.equals(payment.getUserId()))
        {
            throw new ServiceException("支付单不存在");
        }
        markSuccess(payment, payment.getPayNo(), null);
    }

    @Override
    @Transactional
    public void notify(String channel, Map<String, String> headers, String body)
    {
        String normalizedChannel = normalizeChannel(channel);
        PaymentGateway gateway = requireGateway(normalizedChannel);
        NotifyResult notify = gateway.parseNotify(headers, body);
        if (!notify.isSuccess())
        {
            throw new ServiceException("支付回调未成功");
        }
        MallPaymentOrder payment = requireByPayNo(notify.getPayNo());
        if (!normalizedChannel.equals(payment.getChannel()))
        {
            throw new ServiceException("支付渠道不匹配");
        }
        markSuccess(payment, notify.getChannelTradeNo(), body);
    }

    @Override
    public Page<MallPaymentOrder> adminPage(PaymentPageQuery query)
    {
        LambdaQueryWrapper<MallPaymentOrder> wrapper = new LambdaQueryWrapper<MallPaymentOrder>()
                .like(StringUtils.hasText(query.getPayNo()), MallPaymentOrder::getPayNo, query.getPayNo())
                .like(StringUtils.hasText(query.getOrderNo()), MallPaymentOrder::getOrderNo, query.getOrderNo())
                .eq(StringUtils.hasText(query.getChannel()), MallPaymentOrder::getChannel, normalizeChannel(query.getChannel()))
                .eq(StringUtils.hasText(query.getStatus()), MallPaymentOrder::getStatus, query.getStatus())
                .orderByDesc(MallPaymentOrder::getCreateTime)
                .orderByDesc(MallPaymentOrder::getId);
        return mallPaymentOrderMapper.selectPage(new Page<>(pageNum(query.getPageNum()), pageSize(query.getPageSize())), wrapper);
    }

    @Override
    public MallPaymentOrder adminDetail(Long id)
    {
        MallPaymentOrder payment = mallPaymentOrderMapper.selectById(id);
        if (payment == null)
        {
            throw new ServiceException("支付单不存在");
        }
        return payment;
    }

    private void markSuccess(MallPaymentOrder payment, String channelTradeNo, String notifyRaw)
    {
        if (MallPaymentConstants.STATUS_SUCCESS.equals(payment.getStatus()))
        {
            mallOrderService.markOrderPaid(payment.getOrderId(), payment.getPayNo());
            return;
        }
        MallPaymentOrder update = new MallPaymentOrder();
        update.setStatus(MallPaymentConstants.STATUS_SUCCESS);
        update.setActiveFlag(null);
        update.setChannelTradeNo(StringUtils.hasText(channelTradeNo) ? channelTradeNo : payment.getPayNo());
        update.setNotifyRaw(notifyRaw);
        update.setPaidTime(LocalDateTime.now());
        int rows = mallPaymentOrderMapper.update(update, new LambdaUpdateWrapper<MallPaymentOrder>()
                .eq(MallPaymentOrder::getId, payment.getId())
                .ne(MallPaymentOrder::getStatus, MallPaymentConstants.STATUS_SUCCESS)
                .set(MallPaymentOrder::getActiveFlag, null));
        if (rows == 0)
        {
            MallPaymentOrder latest = mallPaymentOrderMapper.selectById(payment.getId());
            if (latest != null && MallPaymentConstants.STATUS_SUCCESS.equals(latest.getStatus()))
            {
                mallOrderService.markOrderPaid(latest.getOrderId(), latest.getPayNo());
                return;
            }
            throw new ServiceException("支付状态更新失败");
        }
        mallOrderService.markOrderPaid(payment.getOrderId(), payment.getPayNo());
    }

    private MallPaymentOrder findActivePayment(Long orderId)
    {
        return mallPaymentOrderMapper.selectOne(new LambdaQueryWrapper<MallPaymentOrder>()
                .eq(MallPaymentOrder::getOrderId, orderId)
                .eq(MallPaymentOrder::getActiveFlag, 1)
                .orderByDesc(MallPaymentOrder::getId)
                .last("LIMIT 1"));
    }

    private boolean isExpired(MallPaymentOrder payment)
    {
        return payment.getExpireTime() != null && payment.getExpireTime().isBefore(LocalDateTime.now());
    }

    private PaymentCreateResult createGatewayPayment(PaymentGateway gateway, MallOrderPaymentView order, String payNo)
    {
        PaymentCreateCmd cmd = new PaymentCreateCmd();
        cmd.setOrderId(order.getOrderId());
        cmd.setOrderNo(order.getOrderNo());
        cmd.setUserId(order.getUserId());
        cmd.setPayNo(payNo);
        cmd.setAmount(order.getPayAmount());
        cmd.setExpireTime(order.getExpireTime());
        return gateway.create(cmd);
    }

    private MallPaymentOrder requireByPayNo(String payNo)
    {
        if (!StringUtils.hasText(payNo))
        {
            throw new ServiceException("支付单号不能为空");
        }
        MallPaymentOrder payment = mallPaymentOrderMapper.selectOne(new LambdaQueryWrapper<MallPaymentOrder>()
                .eq(MallPaymentOrder::getPayNo, payNo));
        if (payment == null)
        {
            throw new ServiceException("支付单不存在");
        }
        return payment;
    }

    private PaymentGateway requireGateway(String channel)
    {
        PaymentGateway gateway = gateways.get(channel);
        if (gateway == null)
        {
            throw new ServiceException("不支持的支付渠道");
        }
        return gateway;
    }

    private String normalizeChannel(String channel)
    {
        if (!StringUtils.hasText(channel))
        {
            throw new ServiceException("支付渠道不能为空");
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private String generatePayNo()
    {
        return LocalDateTime.now().format(PAY_NO_TIME)
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
}
