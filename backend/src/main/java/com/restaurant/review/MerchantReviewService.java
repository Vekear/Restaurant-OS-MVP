package com.restaurant.review;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.exception.BizException;
import com.restaurant.merchant.MerchantGuard;
import com.restaurant.order.Order;
import com.restaurant.order.OrderItem;
import com.restaurant.order.mapper.OrderItemMapper;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.review.mapper.EvaluationMapper;
import com.restaurant.review.vo.ReviewView;
import com.restaurant.user.User;
import com.restaurant.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 商家评价列表（{@code docs/02-api.md §19.1}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantReviewService {

    private final EvaluationMapper evaluationMapper;

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final UserMapper userMapper;

    private final MerchantGuard merchantGuard;

    @Transactional(readOnly = true)
    public PageResult<ReviewView> list(Integer rating, int page, int size) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "rating 取值范围为 1-5");
        }
        Long merchantId = merchantGuard.requireActiveMerchantId();
        Page<Evaluation> query = new Page<>(page, size);
        IPage<Evaluation> result = evaluationMapper.selectPage(query, Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getMerchantId, merchantId)
                .eq(rating != null, Evaluation::getRating, rating)
                .orderByDesc(Evaluation::getCreatedAt)
                .orderByDesc(Evaluation::getId));
        List<Evaluation> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.empty(page, size);
        }
        List<Long> orderIds = records.stream().map(Evaluation::getOrderId).distinct().toList();
        Map<Long, Order> orders = new HashMap<>();
        for (Order order : orderMapper.selectBatchIds(orderIds)) {
            orders.put(order.getId(), order);
        }
        Map<Long, String> userNames = new HashMap<>();
        List<Long> userIds = records.stream().map(Evaluation::getUserId).distinct().toList();
        for (User user : userMapper.selectBatchIds(userIds)) {
            userNames.put(user.getId(), user.getNickname() == null ? user.getUsername() : user.getNickname());
        }
        Map<Long, List<String>> dishNames = new HashMap<>();
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .in(OrderItem::getOrderId, orderIds)
                .orderByAsc(OrderItem::getId));
        for (OrderItem item : items) {
            dishNames.computeIfAbsent(item.getOrderId(), key -> new ArrayList<>()).add(item.getDishName());
        }
        List<ReviewView> views = records.stream()
                .map(evaluation -> {
                    Order order = orders.get(evaluation.getOrderId());
                    return new ReviewView(evaluation.getId(), evaluation.getOrderId(),
                            order == null ? null : order.getOrderType(), order == null ? null : order.getOrderNo(),
                            evaluation.getUserId(), userNames.get(evaluation.getUserId()), evaluation.getRating(),
                            evaluation.getContent(), evaluation.getImageUrls(),
                            dishNames.getOrDefault(evaluation.getOrderId(), List.of()),
                            evaluation.getReply(), evaluation.getCreatedAt());
                })
                .toList();
        return PageResult.of(views, result.getTotal(), page, size);
    }
}
