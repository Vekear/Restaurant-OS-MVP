package com.restaurant.ws;

import com.restaurant.contract.event.OrderStatusChangedEvent;
import com.restaurant.contract.event.PaymentSucceededEvent;
import com.restaurant.common.log.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单事件 → STOMP 推送（架构 §12/§14）：顾客订单主题、商家订单主题、管理端主题。
 *
 * <p>T08 先提供推送能力；T08/T09 的订阅鉴权与原生通道由 ws 安全增强补齐。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        send("/topic/orders/" + event.orderId(), event);
        if (event.merchantId() != null) {
            send("/topic/merchant/" + event.merchantId() + "/orders", event);
        }
        send("/topic/admin/orders", event);
    }

    @EventListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "PAYMENT_SUCCEEDED");
        payload.put("orderId", event.orderId());
        payload.put("payNo", event.payNo());
        payload.put("paidAmount", event.paidAmount());
        payload.put("at", event.paidAt());
        payload.put("traceId", TraceContext.currentTraceId());
        send("/topic/orders/" + event.orderId(), payload);
    }

    private void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (RuntimeException e) {
            log.warn("WebSocket 推送失败 destination={}, reason={}", destination, e.getMessage());
        }
    }
}
