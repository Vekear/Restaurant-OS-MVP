package com.restaurant.order;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OrderType;
import com.restaurant.common.enums.OperatorRole;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.AddressPort;
import com.restaurant.contract.CartClearPort;
import com.restaurant.contract.CouponPort;
import com.restaurant.contract.DishSnapshot;
import com.restaurant.contract.DishSnapshotPort;
import com.restaurant.contract.InventoryPort;
import com.restaurant.contract.MerchantPort;
import com.restaurant.contract.TableReleasePort;
import com.restaurant.contract.TableSessionPort;
import com.restaurant.contract.event.OrderStatusChangedEvent;
import com.restaurant.order.dto.DineInOrderCreateRequest;
import com.restaurant.order.dto.OrderCreateRequest;
import com.restaurant.order.dto.OrderItemRequest;
import com.restaurant.order.mapper.OrderItemMapper;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.order.mapper.OrderStatusLogMapper;
import com.restaurant.order.statemachine.OrderOperator;
import com.restaurant.order.statemachine.OrderStateMachine;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 顾客侧订单写操作：下单（价格服务端重算 + 原子扣库存 + 幂等）、取消、确认收货。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final int MAX_LINES = 50;

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final OrderStatusLogMapper orderStatusLogMapper;

    private final MerchantPort merchantPort;

    private final AddressPort addressPort;

    private final DishSnapshotPort dishSnapshotPort;

    private final InventoryPort inventoryPort;

    private final CouponPort couponPort;

    private final CartClearPort cartClearPort;

    private final TableSessionPort tableSessionPort;

    private final TableReleasePort tableReleasePort;

    private final OrderStateMachine orderStateMachine;

    private final OrderSideEffectService orderSideEffectService;

    private final OrderViewAssembler orderViewAssembler;

    private final ApplicationEventPublisher eventPublisher;

    private final AppProperties appProperties;

    /** 下单（调用方已通过 {@code IdempotencyService} 包裹）。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView create(Long userId, OrderCreateRequest request) {
        MerchantPort.MerchantSnapshot merchant = merchantPort.get(request.getMerchantId());
        requireAvailableMerchant(merchant);
        AddressPort.AddressSnapshot address = addressPort.getOwned(request.getAddressId(), userId);
        if (address == null) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "收货地址不存在或无权访问");
        }

        Map<Long, Integer> merged = mergeItems(request.getItems());
        Map<Long, DishSnapshot> dishes = dishSnapshotPort.loadAll(merged.keySet());

        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            DishSnapshot dish = dishes.get(entry.getKey());
            if (dish == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜品不存在或已删除");
            }
            if (!request.getMerchantId().equals(dish.merchantId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜品不属于该商家");
            }
            if (!"ON_SALE".equals(dish.status())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜品已下架：" + dish.name());
            }
            totalAmount = totalAmount.add(dish.price().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal deliveryFee = merchant.deliveryFee() == null ? BigDecimal.ZERO : merchant.deliveryFee();
        if (merchant.minOrderAmount() != null && totalAmount.compareTo(merchant.minOrderAmount()) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未达起送价 ¥" + merchant.minOrderAmount().toPlainString());
        }
        CouponPort.CouponUsage couponUsage = couponPort.quote(userId, request.getUserCouponId(), totalAmount);
        BigDecimal discount = couponUsage.discountAmount() == null ? BigDecimal.ZERO : couponUsage.discountAmount();
        BigDecimal payAmount = totalAmount.subtract(discount).add(deliveryFee).setScale(2, RoundingMode.HALF_UP);
        if (request.getExpectedPayAmount() != null
                && request.getExpectedPayAmount().setScale(2, RoundingMode.HALF_UP).compareTo(payAmount) != 0) {
            throw new BizException(ErrorCode.AMOUNT_MISMATCH, "订单金额已变化，请刷新后重试");
        }

        // 先原子扣库存（失败回滚整单）
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            inventoryPort.deduct(entry.getKey(), entry.getValue());
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setMerchantId(request.getMerchantId());
        order.setOrderType(OrderType.TAKEOUT.name());
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setAddressId(address.id());
        order.setConsignee(address.consignee());
        order.setConsigneePhone(address.phone());
        order.setDeliveryAddress(joinAddress(address));
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discount);
        order.setDeliveryFee(deliveryFee);
        order.setPayAmount(payAmount);
        order.setCouponId(couponUsage.couponId());
        order.setUserCouponId(couponUsage.userCouponId());
        order.setRemark(request.getRemark());
        order.setTimeoutAt(LocalDateTime.now()
                .plusMinutes(appProperties.getOrder().getPaymentTimeoutMinutes()));
        orderMapper.insert(order);

        int itemCount = 0;
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            DishSnapshot dish = dishes.get(entry.getKey());
            int quantity = entry.getValue();
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setDishId(dish.id());
            item.setDishName(dish.name());
            item.setDishImage(dish.imageUrl());
            item.setPrice(dish.price());
            item.setQuantity(quantity);
            item.setSubtotal(dish.price().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
            orderItemMapper.insert(item);
            itemCount++;
        }

        if (couponUsage.userCouponId() != null) {
            couponPort.consume(couponUsage.userCouponId(), order.getId());
        }

        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(order.getId());
        statusLog.setFromStatus(null);
        statusLog.setToStatus(OrderStatus.PENDING_PAYMENT.name());
        statusLog.setOperatorId(userId);
        statusLog.setOperatorRole(OperatorRole.CUSTOMER.name());
        statusLog.setRemark("下单");
        orderStatusLogMapper.insert(statusLog);

        cartClearPort.clearDishes(userId, merged.keySet());

        eventPublisher.publishEvent(new OrderStatusChangedEvent("ORDER_CREATED", order.getId(), order.getOrderNo(),
                order.getOrderType(), order.getMerchantId(), order.getStatus(),
                OrderStatus.textOf(order.getStatus()), order.getPayAmount(), null, itemCount,
                null, null, LocalDateTime.now(), null));
        log.info("订单创建成功 orderId={}, orderNo={}, userId={}, payAmount={}",
                order.getId(), order.getOrderNo(), userId, payAmount);
        return orderViewAssembler.toDetail(order);
    }


    /**
     * 堂食下单：桌台/商家由 {@code X-Table-Session} 会话决定，不校验起送价、不收取配送费、不清空购物车。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView createDineIn(Long userId, String sessionToken, DineInOrderCreateRequest request) {
        TableSessionPort.TableSessionSnapshot session = tableSessionPort.requireActive(sessionToken);
        MerchantPort.MerchantSnapshot merchant = merchantPort.get(session.merchantId());
        requireAvailableMerchant(merchant);

        Map<Long, Integer> merged = mergeItems(request.getItems());
        Map<Long, DishSnapshot> dishes = dishSnapshotPort.loadAll(merged.keySet());

        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            DishSnapshot dish = dishes.get(entry.getKey());
            if (dish == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜品不存在或已删除");
            }
            if (!session.merchantId().equals(dish.merchantId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜品不属于该商家");
            }
            if (!"ON_SALE".equals(dish.status())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜品已下架：" + dish.name());
            }
            totalAmount = totalAmount.add(dish.price().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        CouponPort.CouponUsage couponUsage = couponPort.quote(userId, request.getUserCouponId(), totalAmount);
        BigDecimal discount = couponUsage.discountAmount() == null
                ? BigDecimal.ZERO : couponUsage.discountAmount();
        BigDecimal payAmount = totalAmount.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        if (request.getExpectedPayAmount() != null
                && request.getExpectedPayAmount().setScale(2, RoundingMode.HALF_UP).compareTo(payAmount) != 0) {
            throw new BizException(ErrorCode.AMOUNT_MISMATCH, "订单金额已变化，请刷新后重试");
        }

        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            inventoryPort.deduct(entry.getKey(), entry.getValue());
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setMerchantId(session.merchantId());
        order.setOrderType(OrderType.DINE_IN.name());
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setTableId(session.tableId());
        order.setTableNo(session.tableNo());
        order.setTableSessionId(session.sessionId());
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discount);
        order.setDeliveryFee(BigDecimal.ZERO.setScale(2));
        order.setPayAmount(payAmount);
        order.setCouponId(couponUsage.couponId());
        order.setUserCouponId(couponUsage.userCouponId());
        order.setRemark(request.getRemark());
        order.setTimeoutAt(LocalDateTime.now()
                .plusMinutes(appProperties.getOrder().getPaymentTimeoutMinutes()));
        orderMapper.insert(order);

        int itemCount = 0;
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            DishSnapshot dish = dishes.get(entry.getKey());
            int quantity = entry.getValue();
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setDishId(dish.id());
            item.setDishName(dish.name());
            item.setDishImage(dish.imageUrl());
            item.setPrice(dish.price());
            item.setQuantity(quantity);
            item.setSubtotal(dish.price().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
            orderItemMapper.insert(item);
            itemCount++;
        }

        if (couponUsage.userCouponId() != null) {
            couponPort.consume(couponUsage.userCouponId(), order.getId());
        }

        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(order.getId());
        statusLog.setFromStatus(null);
        statusLog.setToStatus(OrderStatus.PENDING_PAYMENT.name());
        statusLog.setOperatorId(userId);
        statusLog.setOperatorRole(OperatorRole.CUSTOMER.name());
        statusLog.setRemark("下单");
        orderStatusLogMapper.insert(statusLog);

        tableSessionPort.touchOrder(session.sessionId(), order.getId());

        eventPublisher.publishEvent(new OrderStatusChangedEvent("ORDER_CREATED", order.getId(), order.getOrderNo(),
                order.getOrderType(), order.getMerchantId(), order.getStatus(),
                OrderStatus.textOf(order.getStatus()), order.getPayAmount(), null, itemCount,
                order.getTableId(), order.getTableNo(), LocalDateTime.now(), null));
        log.info("堂食订单创建成功 orderId={}, orderNo={}, userId={}, tableId={}, payAmount={}",
                order.getId(), order.getOrderNo(), userId, order.getTableId(), payAmount);
        return orderViewAssembler.toDetail(order);
    }

    /** 堂食订单取消（仅待支付/待接单），取消成功后同一事务释放桌台会话。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView cancelDineIn(Long userId, Long orderId, String reason) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!OrderType.DINE_IN.name().equals(order.getOrderType())) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        String fromStatus = order.getStatus();
        if (!OrderStatus.PENDING_PAYMENT.name().equals(fromStatus) && !OrderStatus.PAID.name().equals(fromStatus)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "当前状态不允许取消");
        }
        String remark = StringUtils.hasText(reason) ? reason : "用户取消";
        Order updated = orderStateMachine.transition(orderId, OrderStatus.CANCELLED.name(),
                OrderOperator.customer(userId), remark);
        orderSideEffectService.restoreStock(order);
        orderSideEffectService.restoreCoupon(order);
        if (OrderStatus.PAID.name().equals(fromStatus)) {
            orderSideEffectService.refundPaid(orderId);
        } else {
            orderSideEffectService.closeUnpaid(orderId);
        }
        if (order.getTableId() != null && order.getTableSessionId() != null) {
            tableReleasePort.release(order.getTableId(), order.getTableSessionId());
        }
        return orderViewAssembler.toDetail(updated);
    }

    /** 顾客取消（仅待支付/待接单）。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView cancel(Long userId, Long orderId, String reason) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!OrderType.TAKEOUT.name().equals(order.getOrderType())) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        String fromStatus = order.getStatus();
        if (!OrderStatus.PENDING_PAYMENT.name().equals(fromStatus) && !OrderStatus.PAID.name().equals(fromStatus)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "当前状态不允许取消");
        }
        String remark = StringUtils.hasText(reason) ? reason : "用户取消";
        Order updated = orderStateMachine.transition(orderId, OrderStatus.CANCELLED.name(),
                OrderOperator.customer(userId), remark);
        orderSideEffectService.restoreStock(order);
        orderSideEffectService.restoreCoupon(order);
        if (OrderStatus.PAID.name().equals(fromStatus)) {
            orderSideEffectService.refundPaid(orderId);
        } else {
            orderSideEffectService.closeUnpaid(orderId);
        }
        return orderViewAssembler.toDetail(updated);
    }

    /** 确认收货：DELIVERING → COMPLETED。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView confirmReceipt(Long userId, Long orderId) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!OrderType.TAKEOUT.name().equals(order.getOrderType())) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        Order updated = orderStateMachine.transition(orderId, OrderStatus.COMPLETED.name(),
                OrderOperator.customer(userId), "用户确认收货");
        return orderViewAssembler.toDetail(updated);
    }

    private Order requireOwnedOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权操作该订单");
        }
        return order;
    }

    private void requireAvailableMerchant(MerchantPort.MerchantSnapshot merchant) {
        if (merchant == null || !"APPROVED".equals(merchant.auditStatus())
                || !"ENABLED".equals(merchant.status())) {
            throw new BizException(ErrorCode.MERCHANT_UNAVAILABLE, "商家不存在或不可用");
        }
        if ("CLOSED".equals(merchant.businessStatus())) {
            throw new BizException(ErrorCode.MERCHANT_CLOSED, "商家休息中，暂不接单");
        }
    }

    private Map<Long, Integer> mergeItems(List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单至少包含一个菜品");
        }
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            if (item.getDishId() == null || item.getQuantity() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜品与数量不能为空");
            }
            int quantity = merged.merge(item.getDishId(), item.getQuantity(), Integer::sum);
            if (quantity > 99) {
                throw new BizException(ErrorCode.PARAM_INVALID, "同一菜品数量不能超过 99");
            }
        }
        if (merged.size() > MAX_LINES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单次最多 " + MAX_LINES + " 个菜品");
        }
        return merged;
    }

    static String joinAddress(AddressPort.AddressSnapshot address) {
        StringBuilder builder = new StringBuilder();
        append(builder, address.province());
        append(builder, address.city());
        append(builder, address.district());
        append(builder, address.detail());
        return builder.toString();
    }

    private static void append(StringBuilder builder, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(part);
    }

    private static String generateOrderNo() {
        return "T" + LocalDateTime.now().format(ORDER_NO_FORMAT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
