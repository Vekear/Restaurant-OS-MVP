package com.restaurant.contract;

/** 跨域端口：订单评价只读查询（实现方：review 域；消费方：order 域）。 */
public interface ReviewQueryPort {

    /**
     * 查询订单的评价 ID。
     *
     * @param orderId 订单 ID
     * @return 未评价返回 {@code null}
     */
    Long findReviewIdByOrder(Long orderId);
}
