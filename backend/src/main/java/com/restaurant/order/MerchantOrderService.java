package com.restaurant.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OrderType;
import com.restaurant.common.exception.BizException;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.order.statemachine.OrderOperator;
import com.restaurant.order.statemachine.OrderStateMachine;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.order.vo.OrderView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 商家订单操作（/api/v1/merchant/orders/*），所有流转经状态机。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantOrderService {

    private final OrderMapper orderMapper;

    private final OrderStateMachine orderStateMachine;

    private final OrderSideEffectService orderSideEffectService;

    private final OrderViewAssembler orderViewAssembler;

    @Transactional(readOnly = true)
    public PageResult<OrderView> list(Long merchantId, String statusGroup, String status, int page, int size) {
        LambdaQueryWrapper<Order> query = Wrappers.<Order>lambdaQuery()
                .eq(Order::getMerchantId, merchantId)
                .eq(Order::getOrderType, OrderType.TAKEOUT.name());
        OrderQueryService.applyStatusFilter(query, statusGroup, status);
        query.orderByDesc(Order::getCreatedAt).orderByDesc(Order::getId);
        IPage<Order> result = orderMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(orderViewAssembler.toViews(result.getRecords()), result.getTotal(), page, size);
    }

    @Transactional(readOnly = true)
    public OrderDetailView detail(Long merchantId, Long orderId) {
        return orderViewAssembler.toDetail(requireOwnedOrder(merchantId, orderId));
    }

    /** PAID → ACCEPTED。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView accept(Long merchantId, Long orderId) {
        requireOwnedOrder(merchantId, orderId);
        Order updated = orderStateMachine.transition(orderId, OrderStatus.ACCEPTED.name(),
                OrderOperator.merchant(merchantId), "商家接单");
        return orderViewAssembler.toDetail(updated);
    }

    /** PAID → REJECTED，回补库存/优惠券并模拟退款。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView reject(Long merchantId, Long orderId, String reason) {
        if (!StringUtils.hasText(reason) || reason.trim().length() < 2 || reason.length() > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "拒单原因长度需在 2-100 字");
        }
        Order order = requireOwnedOrder(merchantId, orderId);
        Order updated = orderStateMachine.transition(orderId, OrderStatus.REJECTED.name(),
                OrderOperator.merchant(merchantId), reason.trim());
        orderSideEffectService.restoreStock(order);
        orderSideEffectService.restoreCoupon(order);
        orderSideEffectService.refundPaid(orderId);
        return orderViewAssembler.toDetail(updated);
    }

    /** ACCEPTED → MAKING。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView prepare(Long merchantId, Long orderId) {
        requireOwnedOrder(merchantId, orderId);
        Order updated = orderStateMachine.transition(orderId, OrderStatus.MAKING.name(),
                OrderOperator.merchant(merchantId), "开始制作");
        return orderViewAssembler.toDetail(updated);
    }

    /** MAKING → DELIVERING（仅外卖）。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView deliver(Long merchantId, Long orderId) {
        requireOwnedOrder(merchantId, orderId);
        Order updated = orderStateMachine.transition(orderId, OrderStatus.DELIVERING.name(),
                OrderOperator.merchant(merchantId), "开始配送");
        return orderViewAssembler.toDetail(updated);
    }

    /** DELIVERING → COMPLETED。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView complete(Long merchantId, Long orderId) {
        requireOwnedOrder(merchantId, orderId);
        Order updated = orderStateMachine.transition(orderId, OrderStatus.COMPLETED.name(),
                OrderOperator.merchant(merchantId), "商家完成订单");
        return orderViewAssembler.toDetail(updated);
    }

    private Order requireOwnedOrder(Long merchantId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!merchantId.equals(order.getMerchantId())) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权操作该订单");
        }
        return order;
    }
}
