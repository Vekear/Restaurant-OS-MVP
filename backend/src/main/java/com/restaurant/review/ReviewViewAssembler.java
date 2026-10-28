package com.restaurant.review;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.order.Order;
import com.restaurant.order.OrderItem;
import com.restaurant.order.mapper.OrderItemMapper;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.review.vo.ReviewView;
import com.restaurant.user.User;
import com.restaurant.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 评价视图装配（{@code docs/02-api.md §2.3 ReviewView}）。
 *
 * <p>商家评价列表与公开菜品评价列表共用同一装配规则：批量读取订单快照（orderNo/orderType）、
 * 用户展示名（昵称优先、缺省回退用户名）、订单内菜品名快照，避免逐条查询造成 N+1。
 * 只使用订单/用户/菜品展示所需字段，不暴露手机号、地址等 PII。
 */
@Component
@RequiredArgsConstructor
public class ReviewViewAssembler {

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final UserMapper userMapper;

    /**
     * 批量装配评价视图。
     *
     * @param evaluations 评价记录（调用方负责分页与排序）
     * @return 与入参顺序一致的视图列表
     */
    public List<ReviewView> assemble(List<Evaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = evaluations.stream()
                .map(Evaluation::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Order> orders = new HashMap<>();
        if (!orderIds.isEmpty()) {
            for (Order order : orderMapper.selectBatchIds(orderIds)) {
                orders.put(order.getId(), order);
            }
        }

        Map<Long, String> userNames = new HashMap<>();
        List<Long> userIds = evaluations.stream()
                .map(Evaluation::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!userIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(userIds)) {
                userNames.put(user.getId(),
                        user.getNickname() == null || user.getNickname().isBlank()
                                ? user.getUsername() : user.getNickname());
            }
        }

        Map<Long, List<String>> dishNames = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<OrderItem> items = orderItemMapper.selectList(
                    Wrappers.<OrderItem>lambdaQuery()
                            .in(OrderItem::getOrderId, orderIds)
                            .orderByAsc(OrderItem::getId));
            for (OrderItem item : items) {
                dishNames.computeIfAbsent(item.getOrderId(), key -> new ArrayList<>()).add(item.getDishName());
            }
        }

        return evaluations.stream()
                .map(evaluation -> {
                    Order order = orders.get(evaluation.getOrderId());
                    return new ReviewView(evaluation.getId(), evaluation.getOrderId(),
                            order == null ? null : order.getOrderType(),
                            order == null ? null : order.getOrderNo(),
                            evaluation.getUserId(), userNames.get(evaluation.getUserId()),
                            evaluation.getRating(), evaluation.getContent(), evaluation.getImageUrls(),
                            dishNames.getOrDefault(evaluation.getOrderId(), List.of()),
                            evaluation.getReply(), evaluation.getCreatedAt());
                })
                .toList();
    }
}
