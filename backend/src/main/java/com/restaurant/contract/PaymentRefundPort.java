package com.restaurant.contract;

/** 跨域端口：订单取消/拒单/超时时的模拟退款与关闭（实现方：payment 域）。 */
public interface PaymentRefundPort {

    /**
     * 将订单下 SUCCESS 的支付单置为 REFUNDED（TBD-05 模拟退款，无金额退回）。
     *
     * @param orderId 订单 ID
     */
    void refundByOrder(Long orderId);

    /**
     * 将订单下仍为 PENDING 的支付单关闭为 FAILED（未支付订单取消/超时）。
     *
     * @param orderId 订单 ID
     */
    void closePendingByOrder(Long orderId);
}
