package com.restaurant.task;

import com.restaurant.common.config.AppProperties;
import com.restaurant.order.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未支付订单超时取消定时任务（Spring Task，默认每 30 秒）。
 *
 * <p>{@code app.task.enabled=false} 时不注册（测试 profile 关闭定时器，测试直接调用
 * {@link OrderTimeoutService}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.task", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutTask {

    private final OrderTimeoutService orderTimeoutService;

    private final AppProperties appProperties;

    @Scheduled(cron = "${app.order.auto-cancel-cron:0/30 * * * * ?}")
    public void cancelTimedOutOrders() {
        try {
            int cancelled = orderTimeoutService.cancelTimedOut(
                    appProperties.getOrder().getAutoCancelBatchSize());
            if (cancelled > 0) {
                log.info("超时未支付订单已取消数量={}", cancelled);
            }
        } catch (RuntimeException e) {
            log.error("超时订单扫描任务执行失败", e);
        }
    }
}
