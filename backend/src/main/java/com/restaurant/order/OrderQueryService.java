package com.restaurant.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OrderStatusGroup;
import com.restaurant.common.enums.OrderType;
import com.restaurant.common.exception.BizException;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.order.vo.OrderView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 顾客侧订单查询（GET /api/v1/orders、/orders/{id}）。 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderMapper orderMapper;

    private final OrderViewAssembler orderViewAssembler;

    @Transactional(readOnly = true)
    public PageResult<OrderView> list(Long userId, String statusGroup, String status, int page, int size) {
        return listByType(userId, OrderType.TAKEOUT, statusGroup, status, page, size);
    }

    /** 按订单类型分页查询（外卖/堂食），ACTIVE 状态集合随类型不同。 */
    @Transactional(readOnly = true)
    public PageResult<OrderView> listByType(Long userId, OrderType orderType,
                                            String statusGroup, String status, int page, int size) {
        LambdaQueryWrapper<Order> query = Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getOrderType, orderType.name());
        applyStatusFilter(query, statusGroup, status, activeStatuses(orderType));
        query.orderByDesc(Order::getCreatedAt).orderByDesc(Order::getId);
        IPage<Order> result = orderMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(orderViewAssembler.toViews(result.getRecords()), result.getTotal(), page, size);
    }

    @Transactional(readOnly = true)
    public OrderDetailView detail(Long userId, Long orderId) {
        return detailByType(userId, orderId, null);
    }

    /** 顾客侧订单详情；{@code orderType} 非空时校验订单类型，避免跨入口读取。 */
    @Transactional(readOnly = true)
    public OrderDetailView detailByType(Long userId, Long orderId, OrderType orderType) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || (orderType != null && !orderType.name().equals(order.getOrderType()))) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该订单");
        }
        return orderViewAssembler.toDetail(order);
    }

    static List<String> activeStatuses(OrderType orderType) {
        return orderType == OrderType.DINE_IN
                ? OrderStatus.dineInActiveStatuses() : OrderStatus.takeoutActiveStatuses();
    }

    static void applyStatusFilter(LambdaQueryWrapper<Order> query, String statusGroup, String status) {
        applyStatusFilter(query, statusGroup, status, OrderStatus.takeoutActiveStatuses());
    }

    static void applyStatusFilter(LambdaQueryWrapper<Order> query, String statusGroup, String status,
                                  List<String> activeStatuses) {
        if (StringUtils.hasText(status)) {
            if (!OrderStatus.isValid(status)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "非法的订单状态");
            }
            query.eq(Order::getStatus, status);
            return;
        }
        String group = StringUtils.hasText(statusGroup) ? statusGroup : OrderStatusGroup.ALL.name();
        if (!OrderStatusGroup.isValid(group)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的状态分组");
        }
        if (OrderStatusGroup.ACTIVE.name().equals(group)) {
            query.in(Order::getStatus, activeStatuses);
        } else if (OrderStatusGroup.FINISHED.name().equals(group)) {
            query.in(Order::getStatus, List.of(OrderStatus.COMPLETED.name(), OrderStatus.CANCELLED.name(),
                    OrderStatus.REJECTED.name()));
        }
    }
}
