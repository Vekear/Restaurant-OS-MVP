package com.restaurant.task;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring Task 定时任务骨架（{@code docs/01-architecture.md §14}）。
 *
 * <p>单实例部署，@Scheduled 任务统一跑在 {@code restaurant-task-} 线程池；
 * {@code app.task.enabled=false}（测试 profile）时整体关闭，避免后台任务干扰断言。
 * 具体任务（订单超时取消、桌台会话清理）由 T08/T09 在 {@code com.restaurant.task} 下实现。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.task", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("restaurant-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
