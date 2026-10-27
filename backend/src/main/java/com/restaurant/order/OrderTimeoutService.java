package com.restaurant.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.contract.OrderTimeoutPort;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.order.statemachine.OrderOperator;
import com.restaurant.order.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 未支付订单超时取消（Spring Task 周期调用，也可被测试直接调用）。
 *
 * <p>每单独立事务：单笔失败只记录 ERROR 日志，不影响其余订单；重复扫描因状态机前置状态校验而幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutService implements OrderTimeoutPort {

    private final OrderMapper orderMapper;

    private final OrderStateMachine orderStateMachine;

    private final OrderSideEffectService orderSideEffectService;

    private final TransactionTemplate transactionTemplate;

    @Override
    public int cancelTimedOut(int batchSize) {
        int limit = batchSize <= 0 ? 100 : batchSize;
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .le(Order::getTimeoutAt, LocalDateTime.now())
                .orderByAsc(Order::getId)
                .last("LIMIT " + limit));
        if (orders.isEmpty()) {
            return 0;
        }
        long start = System.currentTimeMillis();
        int cancelled = 0;
        for (Order order : orders) {
            Boolean done = transactionTemplate.execute(status -> {
                try {
                    orderStateMachine.transition(order.getId(), OrderStatus.CANCELLED.name(),
                            OrderOperator.system(), "PAYMENT_TIMEOUT");
                    orderSideEffectService.restoreStock(order);
                    orderSideEffectService.restoreCoupon(order);
                    orderSideEffectService.closeUnpaid(order.getId());
                    return true;
                } catch (RuntimeException e) {
                    log.error("订单超时取消失败 orderId={}", order.getId(), e);
                    status.setRollbackOnly();
                    return false;
                }
            });
            if (Boolean.TRUE.equals(done)) {
                cancelled++;
            }
        }
        log.info("超时订单扫描完成 scanned={}, cancelled={}, durationMs={}",
                orders.size(), cancelled, System.currentTimeMillis() - start);
        return cancelled;
    }
}
