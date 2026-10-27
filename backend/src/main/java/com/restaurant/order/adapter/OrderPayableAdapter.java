package com.restaurant.order.adapter;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.OrderPayablePort;
import com.restaurant.order.Order;
import com.restaurant.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link OrderPayablePort} 的订单域实现（支付前校验归属/状态/超时）。 */
@Component
@RequiredArgsConstructor
public class OrderPayableAdapter implements OrderPayablePort {

    private final OrderMapper orderMapper;

    @Override
    public PayableOrder get(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        return order == null ? null : new PayableOrder(order.getId(), order.getUserId(), order.getMerchantId(),
                order.getOrderNo(), order.getOrderType(), order.getStatus(), order.getPayAmount(),
                order.getTimeoutAt());
    }

    @Override
    public PayableOrder requireOwned(Long orderId, Long userId) {
        PayableOrder order = get(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.userId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该订单");
        }
        return order;
    }
}
