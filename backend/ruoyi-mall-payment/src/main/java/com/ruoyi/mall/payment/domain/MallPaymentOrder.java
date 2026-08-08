package com.ruoyi.mall.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("mall_payment_order")
public class MallPaymentOrder
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private String payNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String channel;
    private BigDecimal amount;
    private String status;
    /** 非终态支付单为 1，终态为 null；配合 (order_id, active_flag) 唯一约束防止重复有效支付单。 */
    private Integer activeFlag;
    private String channelTradeNo;
    private String notifyRaw;
    private LocalDateTime paidTime;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;
}
