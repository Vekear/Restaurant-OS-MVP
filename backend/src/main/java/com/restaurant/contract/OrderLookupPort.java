package com.restaurant.contract;

import java.util.List;

/** 跨域端口：订单只读查询（实现方：order 域；消费方：review 域）。 */
public interface OrderLookupPort {

    /**
     * 读取订单摘要。
     *
     * @param orderId 订单 ID
     * @return 不存在返回 {@code null}
     */
    OrderBrief get(Long orderId);

    /**
     * 读取订单内菜品名称快照（用于评价展示）。
     *
     * @param orderId 订单 ID
     * @return 菜品名列表（下单快照顺序）
     */
    List<String> dishNames(Long orderId);


    /**
     * 查询会话下进行中订单 ID（无则返回 {@code null}）。
     *
     * @param sessionId 桌台会话 ID
     * @return 进行中订单 ID
     */
    Long activeOrderIdBySession(Long sessionId);

    /**
     * 会话是否存在进行中订单（PENDING_PAYMENT/PAID/ACCEPTED/MAKING/SERVED）。
     *
     * @param sessionId 桌台会话 ID
     * @return 存在返回 {@code true}
     */
    boolean hasActiveOrderForSession(Long sessionId);

    /**
     * 桌台是否存在进行中订单。
     *
     * @param tableId 桌台 ID
     * @return 存在返回 {@code true}
     */
    boolean hasActiveOrderForTable(Long tableId);

    /**
     * 订单摘要。
     *
     * @param id         订单 ID
     * @param orderNo    订单号
     * @param userId     下单用户
     * @param merchantId 商家 ID
     * @param orderType  TAKEOUT / DINE_IN
     * @param status     订单状态
     */
    record OrderBrief(Long id,
                      String orderNo,
                      Long userId,
                      Long merchantId,
                      String orderType,
                      String status) {
    }
}
