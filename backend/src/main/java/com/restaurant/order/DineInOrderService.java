package com.restaurant.order;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.enums.OrderType;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.order.vo.OrderView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 顾客侧堂食订单查询与取消（{@code docs/02-api.md §11}）。 */
@Service
@RequiredArgsConstructor
public class DineInOrderService {

    private final OrderQueryService orderQueryService;

    private final OrderService orderService;

    public PageResult<OrderView> list(Long userId, String statusGroup, String status, int page, int size) {
        return orderQueryService.listByType(userId, OrderType.DINE_IN, statusGroup, status, page, size);
    }

    public OrderDetailView detail(Long userId, Long orderId) {
        return orderQueryService.detailByType(userId, orderId, OrderType.DINE_IN);
    }

    public OrderDetailView cancel(Long userId, Long orderId, String reason) {
        return orderService.cancelDineIn(userId, orderId, reason);
    }
}
