package com.restaurant.order.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.contract.OrderLookupPort;
import com.restaurant.order.Order;
import com.restaurant.order.OrderItem;
import com.restaurant.order.mapper.OrderItemMapper;
import com.restaurant.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@link OrderLookupPort} 的订单域实现（评价/导出等只读场景）。 */
@Component
@RequiredArgsConstructor
public class OrderLookupAdapter implements OrderLookupPort {

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    @Override
    public OrderBrief get(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }
        return new OrderBrief(order.getId(), order.getOrderNo(), order.getUserId(), order.getMerchantId(),
                order.getOrderType(), order.getStatus());
    }

    @Override
    public List<String> dishNames(Long orderId) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .eq(OrderItem::getOrderId, orderId)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .map(OrderItem::getDishName)
                .toList();
    }

    @Override
    public Long activeOrderIdBySession(Long sessionId) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getTableSessionId, sessionId)
                .in(Order::getStatus, OrderStatus.dineInActiveStatuses())
                .orderByDesc(Order::getId)
                .last("LIMIT 1"));
        return order == null ? null : order.getId();
    }

    @Override
    public boolean hasActiveOrderForSession(Long sessionId) {
        return orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getTableSessionId, sessionId)
                .in(Order::getStatus, OrderStatus.dineInActiveStatuses())) > 0;
    }

    @Override
    public boolean hasActiveOrderForTable(Long tableId) {
        return orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getTableId, tableId)
                .in(Order::getStatus, OrderStatus.dineInActiveStatuses())) > 0;
    }
}
