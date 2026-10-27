package com.restaurant.order.statemachine;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.enums.OperatorRole;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OrderType;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.event.OrderStatusChangedEvent;
import com.restaurant.order.Order;
import com.restaurant.order.OrderStatusLog;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.order.mapper.OrderStatusLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 订单状态机唯一入口（架构 §11.3）：状态合法性 + 操作者权限 + 乐观锁并发控制 + 状态日志 + 事件发布。
 *
 * <p>禁止任何服务直接 {@code UPDATE orders.status}，所有流转必须经过 {@link #transition}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateMachine {

    private static final Set<String> TERMINAL = Set.of(OrderStatus.COMPLETED.name(),
            OrderStatus.CANCELLED.name(), OrderStatus.REJECTED.name());

    private final OrderMapper orderMapper;

    private final OrderStatusLogMapper orderStatusLogMapper;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 原子流转订单状态。
     *
     * @param orderId      订单 ID
     * @param targetStatus 目标状态（{@link OrderStatus} 名称）
     * @param operator     操作者
     * @param remark       备注（取消/拒单原因等，最长 255，由调用方校验）
     * @return 流转后的订单
     */
    @Transactional(rollbackFor = Exception.class)
    public Order transition(Long orderId, String targetStatus, OrderOperator operator, String remark) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        String fromStatus = order.getStatus();
        if (!OrderStatus.isValid(targetStatus)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的目标状态");
        }
        if (TERMINAL.contains(fromStatus)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单已结束，不能变更状态");
        }
        verifyTransition(order, fromStatus, targetStatus, operator);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime paidAt = OrderStatus.PAID.name().equals(targetStatus) ? now : null;
        LocalDateTime finishedAt = OrderStatus.COMPLETED.name().equals(targetStatus) ? now : null;
        String cancelReason = (OrderStatus.CANCELLED.name().equals(targetStatus)
                || OrderStatus.REJECTED.name().equals(targetStatus)) ? remark : null;

        int rows = orderMapper.transit(orderId, fromStatus, order.getVersion(), targetStatus,
                paidAt, finishedAt, cancelReason);
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
        }

        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(targetStatus);
        statusLog.setOperatorId(operator.id());
        statusLog.setOperatorRole(operator.role().name());
        statusLog.setRemark(trimRemark(remark));
        orderStatusLogMapper.insert(statusLog);

        Order updated = orderMapper.selectById(orderId);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                eventType(updated.getStatus()),
                updated.getId(),
                updated.getOrderNo(),
                updated.getOrderType(),
                updated.getMerchantId(),
                updated.getStatus(),
                OrderStatus.textOf(updated.getStatus()),
                updated.getPayAmount(),
                null,
                0,
                updated.getTableId(),
                updated.getTableNo(),
                now,
                null));
        log.info("订单状态流转 id={}, {} -> {}, operatorRole={}, operatorId={}",
                orderId, fromStatus, targetStatus, operator.role(), operator.id());
        return updated;
    }

    /** 订单类型 + 当前状态 + 操作者三者的合法性矩阵。 */
    private void verifyTransition(Order order, String fromStatus, String targetStatus, OrderOperator operator) {
        OperatorRole role = operator.role();
        boolean merchantOwns = role == OperatorRole.MERCHANT && operator.id().equals(order.getMerchantId());
        boolean customerOwns = role == OperatorRole.CUSTOMER && operator.id().equals(order.getUserId());
        boolean takeout = OrderType.TAKEOUT.name().equals(order.getOrderType());
        boolean allowed = switch (targetStatus) {
            case "PAID" -> role == OperatorRole.SYSTEM && "PENDING_PAYMENT".equals(fromStatus);
            case "CANCELLED" -> (customerOwns && ("PENDING_PAYMENT".equals(fromStatus) || "PAID".equals(fromStatus)))
                    || (role == OperatorRole.SYSTEM && "PENDING_PAYMENT".equals(fromStatus));
            case "REJECTED" -> merchantOwns && "PAID".equals(fromStatus);
            case "ACCEPTED" -> merchantOwns && "PAID".equals(fromStatus);
            case "MAKING" -> merchantOwns && "ACCEPTED".equals(fromStatus);
            case "DELIVERING" -> merchantOwns && takeout && "MAKING".equals(fromStatus);
            case "SERVED" -> merchantOwns && !takeout && "MAKING".equals(fromStatus);
            case "COMPLETED" -> (merchantOwns || customerOwns)
                    && ((takeout && "DELIVERING".equals(fromStatus)) || (!takeout && "SERVED".equals(fromStatus)));
            default -> false;
        };
        if (!allowed) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态不允许该操作");
        }
    }

    private String eventType(String status) {
        return OrderStatus.CANCELLED.name().equals(status) ? "ORDER_STATUS_CHANGED" : "ORDER_STATUS_CHANGED";
    }

    private String trimRemark(String remark) {
        if (remark == null) {
            return null;
        }
        return remark.length() <= 255 ? remark : remark.substring(0, 255);
    }
}
