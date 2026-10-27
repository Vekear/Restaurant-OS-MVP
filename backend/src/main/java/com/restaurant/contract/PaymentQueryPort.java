package com.restaurant.contract;

/** 跨域端口：支付单只读查询（实现方：payment 域）。 */
public interface PaymentQueryPort {

    /**
     * 读取订单最新一笔支付单。
     *
     * @param orderId 订单 ID
     * @return 无支付单返回 {@code null}
     */
    PaymentSnapshot latestByOrder(Long orderId);
}
