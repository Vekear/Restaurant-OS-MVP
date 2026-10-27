package com.restaurant.order;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表 {@code orders}（外卖 + 堂食共用，{@code order_type} 区分）。
 *
 * <p>{@code pay_amount = total_amount - discount_amount + delivery_fee}，
 * 是展示与报表的唯一金额口径；收货人/电话/地址为下单时快照。
 */
@Getter
@Setter
@TableName("orders")
public class Order extends BaseEntity {

    private String orderNo;

    private Long userId;

    private Long merchantId;

    /** TAKEOUT / DINE_IN。 */
    private String orderType;

    /** 见 {@link com.restaurant.common.enums.OrderStatus}。 */
    private String status;

    private Long tableId;

    private String tableNo;

    private Long tableSessionId;

    private Long addressId;

    private String consignee;

    private String consigneePhone;

    private String deliveryAddress;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal deliveryFee;

    private BigDecimal payAmount;

    private Long couponId;

    private Long userCouponId;

    private String remark;

    private String cancelReason;

    /** 支付超时时间（Spring Task 扫描）。 */
    private LocalDateTime timeoutAt;

    private LocalDateTime paidAt;

    private LocalDateTime finishedAt;

    @Version
    private Integer version;
}
