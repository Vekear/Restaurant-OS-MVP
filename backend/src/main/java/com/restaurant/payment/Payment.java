package com.restaurant.payment;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单 {@code payment}：{@code order_amount} 为订单真实应付，
 * {@code paid_amount = min(app.payment.mock-charge-amount, order_amount)} 为模拟实扣。
 */
@Getter
@Setter
@TableName("payment")
public class Payment extends BaseEntity {

    private String payNo;

    private Long orderId;

    private Long userId;

    private BigDecimal orderAmount;

    private BigDecimal paidAmount;

    private String channel;

    /** PENDING / SUCCESS / FAILED / REFUNDED。 */
    private String status;

    private String transactionNo;

    private LocalDateTime expireAt;

    private LocalDateTime paidAt;

    private LocalDateTime callbackAt;

    @Version
    private Integer version;
}
