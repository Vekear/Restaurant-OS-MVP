package com.restaurant.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OrderType;
import com.restaurant.contract.MerchantPort;
import com.restaurant.contract.ReviewQueryPort;
import com.restaurant.order.mapper.OrderItemMapper;
import com.restaurant.order.mapper.OrderStatusLogMapper;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.order.vo.OrderItemView;
import com.restaurant.order.vo.OrderView;
import com.restaurant.order.vo.StatusLogView;
import com.restaurant.payment.Payment;
import com.restaurant.payment.mapper.PaymentMapper;
import com.restaurant.payment.vo.PaymentView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单视图装配器：批量取明细/日志/支付，避免列表 N+1 查询。
 */
@Component
@RequiredArgsConstructor
public class OrderViewAssembler {

    private final OrderItemMapper orderItemMapper;

    private final OrderStatusLogMapper orderStatusLogMapper;

    private final PaymentMapper paymentMapper;

    private final MerchantPort merchantPort;

    private final ReviewQueryPort reviewQueryPort;

    /** 列表视图（批量）。 */
    public List<OrderView> toViews(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> counts = countItems(orders.stream().map(Order::getId).toList());
        List<OrderView> views = new ArrayList<>(orders.size());
        for (Order order : orders) {
            views.add(toView(order, counts.getOrDefault(order.getId(), 0)));
        }
        return views;
    }

    public OrderView toView(Order order, int itemCount) {
        return new OrderView(order.getId(), order.getOrderNo(), order.getOrderType(), order.getStatus(),
                OrderStatus.textOf(order.getStatus()), order.getPayAmount(), order.getTotalAmount(),
                order.getDiscountAmount(), order.getDeliveryFee(), itemCount, order.getTableNo(),
                order.getCreatedAt(), order.getPaidAt(), order.getFinishedAt());
    }

    /** 详情视图。 */
    public OrderDetailView toDetail(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId));
        List<OrderStatusLog> logs = orderStatusLogMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                .eq(OrderStatusLog::getOrderId, order.getId())
                .orderByAsc(OrderStatusLog::getId));
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, order.getId())
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        String merchantName = null;
        MerchantPort.MerchantSnapshot merchant = merchantPort.get(order.getMerchantId());
        if (merchant != null) {
            merchantName = merchant.name();
        }
        Long reviewId = reviewQueryPort.findReviewIdByOrder(order.getId());
        boolean takeout = OrderType.TAKEOUT.name().equals(order.getOrderType());
        boolean timeout = order.getTimeoutAt() != null && order.getTimeoutAt().isBefore(LocalDateTime.now());
        boolean canPay = OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus()) && !timeout
                && (payment == null || !"SUCCESS".equals(payment.getStatus()));
        boolean canCancel = OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())
                || OrderStatus.PAID.name().equals(order.getStatus());
        boolean canConfirmReceipt = takeout && OrderStatus.DELIVERING.name().equals(order.getStatus());
        boolean canReview = OrderStatus.COMPLETED.name().equals(order.getStatus()) && reviewId == null;

        List<OrderItemView> itemViews = items.stream()
                .map(item -> new OrderItemView(item.getId(), item.getDishId(), item.getDishName(),
                        item.getDishImage(), item.getPrice(), item.getQuantity(), item.getSubtotal()))
                .toList();
        List<StatusLogView> logViews = logs.stream()
                .map(log -> new StatusLogView(log.getFromStatus(), log.getToStatus(), log.getOperatorRole(),
                        log.getOperatorId(), log.getRemark(), log.getCreatedAt()))
                .toList();
        PaymentView paymentView = payment == null ? null : toPaymentView(payment, order);

        return new OrderDetailView(order.getId(), order.getOrderNo(), order.getOrderType(), order.getStatus(),
                OrderStatus.textOf(order.getStatus()), order.getPayAmount(), order.getTotalAmount(),
                order.getDiscountAmount(), order.getDeliveryFee(), items.size(), order.getTableNo(),
                order.getCreatedAt(), order.getPaidAt(), order.getFinishedAt(),
                order.getUserId(), order.getMerchantId(), merchantName,
                itemViews, order.getConsignee(), order.getConsigneePhone(), order.getDeliveryAddress(),
                order.getTableId(), order.getTableSessionId(), order.getRemark(), order.getCancelReason(),
                order.getTimeoutAt(), paymentView, logViews,
                canPay, canCancel, canConfirmReceipt, canReview, reviewId);
    }

    public PaymentView toPaymentView(Payment payment, Order order) {
        return new PaymentView(payment.getPayNo(), payment.getOrderId(), order.getOrderType(),
                order.getOrderNo(), payment.getOrderAmount(), payment.getPaidAmount(), payment.getChannel(),
                payment.getStatus(), payment.getTransactionNo(), payment.getPaidAt(),
                payment.getCallbackAt(), payment.getExpireAt(), payment.getCreatedAt());
    }

    private Map<Long, Integer> countItems(Collection<Long> orderIds) {
        Map<Long, Integer> counts = new HashMap<>();
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .in(OrderItem::getOrderId, orderIds)
                .select(OrderItem::getOrderId, OrderItem::getQuantity));
        for (OrderItem item : items) {
            counts.merge(item.getOrderId(), 1, Integer::sum);
        }
        return counts;
    }

    /** 金额按两位小数输出（DECIMAL(10,2) 已保证，此处防御性 setScale）。 */
    public static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
