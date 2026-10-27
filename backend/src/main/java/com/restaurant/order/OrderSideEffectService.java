package com.restaurant.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.contract.CouponPort;
import com.restaurant.contract.InventoryPort;
import com.restaurant.contract.PaymentRefundPort;
import com.restaurant.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单取消/拒单/超时的补偿动作：回补库存、回补优惠券、关闭或退款支付单。
 *
 * <p>仅在状态机流转成功后调用，保证不重复回补。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSideEffectService {

    private final OrderItemMapper orderItemMapper;

    private final InventoryPort inventoryPort;

    private final CouponPort couponPort;

    private final PaymentRefundPort paymentRefundPort;

    /** 按订单明细逐项回补库存（业务侧保证只调用一次）。 */
    public void restoreStock(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            if (item.getDishId() != null && item.getQuantity() != null) {
                inventoryPort.restore(item.getDishId(), item.getQuantity());
            }
        }
    }

    public void restoreCoupon(Order order) {
        couponPort.restore(order.getUserCouponId());
    }

    /** 已支付订单取消/拒单：SUCCESS → REFUNDED。 */
    public void refundPaid(Long orderId) {
        paymentRefundPort.refundByOrder(orderId);
    }

    /** 未支付订单取消/超时：PENDING → FAILED。 */
    public void closeUnpaid(Long orderId) {
        paymentRefundPort.closePendingByOrder(orderId);
    }
}
