package com.restaurant.table.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.contract.OrderLookupPort;
import com.restaurant.contract.TableReleasePort;
import com.restaurant.table.TableSession;
import com.restaurant.table.mapper.TableSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过期桌台会话清理（{@code docs/01-architecture.md §14}）。
 *
 * <p>仅清理“已过期且无进行中订单”的会话：每桌独立事务，单桌失败不影响其余会话；
 * 有进行中订单的会话跳过，等待订单完成或用户关台。独立为 Service 便于测试直接调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableSessionCleanupService {

    private static final int BATCH_SIZE = 200;

    private final TableSessionMapper tableSessionMapper;

    private final OrderLookupPort orderLookupPort;

    private final TableReleasePort tableReleasePort;

    private final TransactionTemplate transactionTemplate;

    public int cleanupExpiredSessions() {
        long start = System.currentTimeMillis();
        List<TableSession> sessions = tableSessionMapper.selectList(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getStatus, "ACTIVE")
                .le(TableSession::getExpireAt, LocalDateTime.now())
                .orderByAsc(TableSession::getId)
                .last("LIMIT " + BATCH_SIZE));
        if (sessions.isEmpty()) {
            return 0;
        }
        int closed = 0;
        int skipped = 0;
        for (TableSession session : sessions) {
            if (orderLookupPort.hasActiveOrderForSession(session.getId())) {
                skipped++;
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> tableReleasePort.release(
                        session.getTableId(), session.getId()));
                closed++;
            } catch (RuntimeException e) {
                log.error("桌台会话清理失败 sessionId={}, tableId={}", session.getId(), session.getTableId(), e);
            }
        }
        log.info("桌台会话清理完成 scanned={}, closed={}, skippedActiveOrders={}, durationMs={}",
                sessions.size(), closed, skipped, System.currentTimeMillis() - start);
        return closed;
    }
}
