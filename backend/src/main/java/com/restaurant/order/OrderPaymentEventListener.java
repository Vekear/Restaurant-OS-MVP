package com.restaurant.order;

import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OperatorRole;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.event.PaymentSucceededEvent;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.order.statemachine.OrderOperator;
import com.restaurant.order.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 支付成功事件 → 订单状态机：PENDING_PAYMENT → PAID（仅 SYSTEM 可触发）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventListener {

    private final OrderMapper orderMapper;

    private final OrderStateMachine orderStateMachine;

    @EventListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Order order = orderMapper.selectById(event.orderId());
        if (order == null) {
            log.warn("支付成功事件找不到订单 orderId={}", event.orderId());
            return;
        }
        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            return;
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            log.warn("订单状态非待支付，跳过置为已支付 orderId={}, status={}", order.getId(), order.getStatus());
            return;
        }
        try {
            orderStateMachine.transition(order.getId(), OrderStatus.PAID.name(),
                    new OrderOperator(0L, OperatorRole.SYSTEM), "支付成功");
        } catch (BizException e) {
            log.warn("订单置为已支付失败 orderId={}, code={}, message={}",
                    order.getId(), e.getCode(), e.getMessage());
        }
    }
}
