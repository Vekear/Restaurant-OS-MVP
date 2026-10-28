package com.restaurant.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.admin.vo.AdminOrderView;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OrderType;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.util.DateRanges;
import com.restaurant.merchant.Merchant;
import com.restaurant.merchant.mapper.MerchantMapper;
import com.restaurant.order.Order;
import com.restaurant.order.OrderViewAssembler;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.order.vo.OrderView;
import com.restaurant.user.User;
import com.restaurant.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 管理后台全量订单查询（{@code docs/02-api.md §20.6–20.7}）。 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderMapper orderMapper;

    private final OrderViewAssembler orderViewAssembler;

    private final MerchantMapper merchantMapper;

    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public PageResult<AdminOrderView> list(Long merchantId, String orderType, String status, String startDate,
                                           String endDate, String keyword, int page, int size) {
        validateOrderType(orderType);
        validateStatus(status);
        DateRanges.Range range = resolveRange(startDate, endDate);
        LocalDateTime start = DateRanges.parseOptional(startDate, "startDate") == null ? null : range.startTime();
        LocalDateTime end = DateRanges.parseOptional(endDate, "endDate") == null ? null : range.endTime();
        var query = Wrappers.<Order>lambdaQuery()
                .eq(merchantId != null, Order::getMerchantId, merchantId)
                .eq(orderType != null && !orderType.isBlank(), Order::getOrderType, orderType)
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .ge(start != null, Order::getCreatedAt, start)
                .le(end != null, Order::getCreatedAt, end)
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            if (value.length() > 30) {
                throw new BizException(ErrorCode.PARAM_INVALID, "keyword 不能超过 30 字");
            }
            query.and(w -> w.like(Order::getOrderNo, value).or().like(Order::getConsignee, value));
        }
        IPage<Order> result = orderMapper.selectPage(new Page<>(page, size), query);
        List<OrderView> views = orderViewAssembler.toViews(result.getRecords());
        Map<Long, Order> byId = new LinkedHashMap<>();
        result.getRecords().forEach(order -> byId.put(order.getId(), order));
        Map<Long, String> merchantNames = merchantNames(result.getRecords());
        Map<Long, String> usernames = usernames(result.getRecords());
        List<AdminOrderView> adminViews = views.stream()
                .map(view -> {
                    Order order = byId.get(view.id());
                    return new AdminOrderView(view.id(), view.orderNo(), view.orderType(), view.status(),
                            view.statusText(), view.payAmount(), view.totalAmount(), view.discountAmount(),
                            view.deliveryFee(), view.itemCount(), view.tableNo(), view.createdAt(),
                            view.paidAt(), view.finishedAt(), order.getMerchantId(),
                            merchantNames.get(order.getMerchantId()), usernames.get(order.getUserId()));
                })
                .toList();
        return PageResult.of(adminViews, result.getTotal(), page, size);
    }

    @Transactional(readOnly = true)
    public OrderDetailView detail(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return orderViewAssembler.toDetail(order);
    }

    private Map<Long, String> merchantNames(List<Order> orders) {
        List<Long> ids = orders.stream().map(Order::getMerchantId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (Merchant merchant : merchantMapper.selectBatchIds(ids)) {
            names.put(merchant.getId(), merchant.getName());
        }
        return names;
    }

    private Map<Long, String> usernames(List<Order> orders) {
        List<Long> ids = orders.stream().map(Order::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (User user : userMapper.selectBatchIds(ids)) {
            names.put(user.getId(), user.getUsername());
        }
        return names;
    }

    private static DateRanges.Range resolveRange(String startDate, String endDate) {
        DateRanges.Range defaults = new DateRanges.Range(DateRanges.today().minusDays(29), DateRanges.today());
        return DateRanges.resolve(DateRanges.parseOptional(startDate, "startDate"),
                DateRanges.parseOptional(endDate, "endDate"), defaults.start(), defaults.end(), 92);
    }

    private static void validateOrderType(String orderType) {
        if (orderType != null && !orderType.isBlank() && !OrderType.isValid(orderType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的订单类型");
        }
    }

    private static void validateStatus(String status) {
        if (status != null && !status.isBlank() && !OrderStatus.isValid(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的订单状态");
        }
    }
}
