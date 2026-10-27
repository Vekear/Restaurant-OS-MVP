package com.restaurant.contract;

/** 跨域端口：扫描并取消超时未支付订单（实现方：order 域；调用方：task 域）。 */
public interface OrderTimeoutPort {

    /**
     * 扫描 {@code PENDING_PAYMENT AND timeout_at <= now()} 的订单并逐单取消（回补库存/优惠券、关闭支付单）。
     *
     * @param batchSize 单批最大处理条数
     * @return 本次实际取消的订单数
     */
    int cancelTimedOut(int batchSize);
}
