package com.restaurant.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 跨域端口：订单可支付性校验与快照（实现方：order 域）。 */
public interface OrderPayablePort {

    /**
     * 按 ID 读取订单支付快照。
     *
     * @param orderId 订单 ID
     * @return 不存在返回 {@code null}
     */
    PayableOrder get(Long orderId);

    /**
     * 读取订单并校验归属。
     *
     * @param orderId 订单 ID
     * @param userId  用户 ID
     * @return 订单支付快照
     * @throws com.restaurant.common.exception.BizException 40401 订单不存在 / 40302 非本人订单
     */
    PayableOrder requireOwned(Long orderId, Long userId);

    /**
     * 订单支付快照。
     *
     * @param orderId   订单 ID
     * @param userId    下单用户
     * @param merchantId 商家 ID
     * @param orderNo   订单号
     * @param orderType  TAKEOUT / DINE_IN
     * @param status    订单状态
     * @param payAmount 应付金额
     * @param timeoutAt 支付超时时间
     */
    record PayableOrder(Long orderId,
                        Long userId,
                        Long merchantId,
                        String orderNo,
                        String orderType,
                        String status,
                        BigDecimal payAmount,
                        LocalDateTime timeoutAt) {
    }
}
