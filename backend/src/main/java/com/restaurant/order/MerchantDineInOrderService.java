package com.restaurant.order;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OrderType;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.TableReleasePort;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.order.statemachine.OrderOperator;
import com.restaurant.order.statemachine.OrderStateMachine;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.order.OrderViewAssembler;
import com.restaurant.order.vo.OrderView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商家堂食订单（{@code docs/02-api.md §18.8–§18.12}）：接单/制作/出餐/完成。
 *
 * <p>完成后在同一事务内把桌台置为 FREE 并关闭会话；任一步失败整体回滚，避免“订单已完成但桌台仍占用”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantDineInOrderService {

    private final OrderMapper orderMapper;

    private final OrderStateMachine orderStateMachine;

    private final MerchantOrderService merchantOrderService;

    private final TableReleasePort tableReleasePort;

    private final OrderViewAssembler orderViewAssembler;

    public PageResult<OrderView> list(Long merchantId, String statusGroup, String status, int page, int size) {
        return merchantOrderService.listByType(merchantId, OrderType.DINE_IN, statusGroup, status, page, size);
    }

    public OrderDetailView detail(Long merchantId, Long orderId) {
        return merchantOrderService.detailByType(merchantId, orderId, OrderType.DINE_IN);
    }

    /** PAID → ACCEPTED。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView accept(Long merchantId, Long orderId) {
        requireOwnedDineInOrder(merchantId, orderId);
        return transition(merchantId, orderId, OrderStatus.ACCEPTED, "商家接单");
    }

    /** ACCEPTED → MAKING。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView prepare(Long merchantId, Long orderId) {
        requireOwnedDineInOrder(merchantId, orderId);
        return transition(merchantId, orderId, OrderStatus.MAKING, "开始制作");
    }

    /** MAKING → SERVED。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView serve(Long merchantId, Long orderId) {
        requireOwnedDineInOrder(merchantId, orderId);
        return transition(merchantId, orderId, OrderStatus.SERVED, "已出餐");
    }

    /** SERVED → COMPLETED，并释放桌台会话。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailView complete(Long merchantId, Long orderId) {
        Order order = requireOwnedDineInOrder(merchantId, orderId);
        Order updated = orderStateMachine.transition(orderId, OrderStatus.COMPLETED.name(),
                OrderOperator.merchant(merchantId), "订单完成");
        if (order.getTableId() != null && order.getTableSessionId() != null) {
            tableReleasePort.release(order.getTableId(), order.getTableSessionId());
        }
        log.info("堂食订单完成并释放桌台 orderId={}, tableId={}, sessionId={}",
                orderId, order.getTableId(), order.getTableSessionId());
        return orderViewAssembler.toDetail(updated);
    }

    private OrderDetailView transition(Long merchantId, Long orderId, OrderStatus target, String remark) {
        Order updated = orderStateMachine.transition(orderId, target.name(),
                OrderOperator.merchant(merchantId), remark);
        return orderViewAssembler.toDetail(updated);
    }

    private Order requireOwnedDineInOrder(Long merchantId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!merchantId.equals(order.getMerchantId())) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权操作该订单");
        }
        if (!OrderType.DINE_IN.name().equals(order.getOrderType())) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }
}
