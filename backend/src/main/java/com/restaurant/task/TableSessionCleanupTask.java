package com.restaurant.task;

import com.restaurant.table.service.TableSessionCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 桌台会话清理定时任务（cron 由 {@code app.table.session-cleanup-cron} 配置）。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.task", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TableSessionCleanupTask {

    private final TableSessionCleanupService tableSessionCleanupService;

    @Scheduled(cron = "${app.table.session-cleanup-cron:0 */5 * * * * ?}")
    public void cleanupIdleSessions() {
        try {
            int closed = tableSessionCleanupService.cleanupExpiredSessions();
            if (closed > 0) {
                log.info("桌台会话清理任务完成 closed={}", closed);
            }
        } catch (RuntimeException e) {
            log.error("桌台会话清理任务异常", e);
        }
    }
}
