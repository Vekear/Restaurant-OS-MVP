package com.restaurant.report.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单导出行（订单 + 最近一次支付 + 菜品明细拼接）。 */
@Data
public class OrderExportRow {

    private Long id;

    private String orderNo;

    private String merchantName;

    private LocalDateTime createdAt;

    private String orderType;

    private String status;

    private String consignee;

    private String consigneePhone;

    private String deliveryAddress;

    private String dishSummary;

    private BigDecimal totalAmount;

    private BigDecimal deliveryFee;

    private BigDecimal discountAmount;

    private BigDecimal payAmount;

    private BigDecimal mockPaidAmount;

    private String paymentStatus;
}
