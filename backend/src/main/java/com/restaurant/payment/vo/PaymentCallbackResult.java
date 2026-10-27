package com.restaurant.payment.vo;

/** 回调应答体（契约固定字段，不经统一响应包裹：{@code {received,payNo,status}}）。 */
public record PaymentCallbackResult(boolean received, String payNo, String status) {
}
