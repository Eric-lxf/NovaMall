package com.ruoyi.mall.payment.gateway.impl;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.mall.payment.constant.MallPaymentConstants;
import com.ruoyi.mall.payment.gateway.NotifyResult;
import com.ruoyi.mall.payment.gateway.PaymentCreateCmd;
import com.ruoyi.mall.payment.gateway.PaymentCreateResult;
import com.ruoyi.mall.payment.gateway.PaymentGateway;

@Component
@ConditionalOnProperty(prefix = "mall.payment", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockPayGateway implements PaymentGateway
{
    @Override
    public String channel()
    {
        return MallPaymentConstants.CHANNEL_MOCK;
    }

    @Override
    public PaymentCreateResult create(PaymentCreateCmd cmd)
    {
        PaymentCreateResult result = new PaymentCreateResult();
        result.setChannel(channel());
        result.setPayNo(cmd.getPayNo());
        result.setMock(true);
        result.setPayUrl("/mall/payments/mock/confirm");
        result.getParams().put("payNo", cmd.getPayNo());
        result.getParams().put("orderId", cmd.getOrderId());
        result.getParams().put("orderNo", cmd.getOrderNo());
        result.getParams().put("amount", cmd.getAmount());
        result.getParams().put("expireTime", cmd.getExpireTime());
        return result;
    }

    @Override
    public NotifyResult parseNotify(Map<String, String> headers, String body)
    {
        JSONObject json;
        try
        {
            json = JSON.parseObject(body);
        }
        catch (Exception e)
        {
            throw new ServiceException("Mock支付回调报文不合法");
        }
        NotifyResult result = new NotifyResult();
        result.setPayNo(json.getString("payNo"));
        result.setSuccess(Boolean.TRUE.equals(json.getBoolean("success")));
        result.setChannelTradeNo(json.getString("payNo"));
        return result;
    }
}
