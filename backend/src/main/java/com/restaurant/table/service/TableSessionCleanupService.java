package com.restaurant.table.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.contract.OrderLookupPort;
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
 * 有进行中订单的会话跳过，等待订单完成或用户关台。清理路径统一走
 * {@link TableReleaseService#releaseExpired(Long, Long)}：同一事务内 会话→CLOSED、
 * 桌台→FREE 并发布 TABLE_SESSION_CLOSED。
 *
 * <p>另做一轮补救扫描：旧版本惰性过期只置 EXPIRED、不释放桌台，导致桌台长期 OCCUPIED；
 * 这里对“EXPIRED 且仍被桌台引用”的会话补记 CLOSED 并释放桌台。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableSessionCleanupService {

    private static final int BATCH_SIZE = 200;

    private final TableSessionMapper tableSessionMapper;

    private final OrderLookupPort orderLookupPort;

    private final TableReleaseService tableReleaseService;

    private final TransactionTemplate transactionTemplate;

    public int cleanupExpiredSessions() {
        long start = System.currentTimeMillis();
        int closed = cleanupExpiredActiveSessions();
        int repaired = repairLegacyExpiredSessions();
        log.info("桌台会话清理完成 closed={}, repaired={}, durationMs={}",
                closed, repaired, System.currentTimeMillis() - start);
        return closed + repaired;
    }

    /** 扫描 ACTIVE 且 expire_at 已过的会话，逐桌独立事务释放。 */
    private int cleanupExpiredActiveSessions() {
        List<TableSession> sessions = tableSessionMapper.selectList(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getStatus, "ACTIVE")
                .le(TableSession::getExpireAt, LocalDateTime.now())
                .orderByAsc(TableSession::getId)
                .last("LIMIT " + BATCH_SIZE));
        int closed = 0;
        int skipped = 0;
        for (TableSession session : sessions) {
            if (orderLookupPort.hasActiveOrderForSession(session.getId())) {
                skipped++;
                continue;
            }
            try {
                Boolean released = transactionTemplate.execute(status -> tableReleaseService.releaseExpired(
                        session.getTableId(), session.getId()));
                if (Boolean.TRUE.equals(released)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                log.warn("桌台会话清理失败 sessionId={}, tableId={}, reason={}",
                        session.getId(), session.getTableId(), e.getMessage());
            }
        }
        if (!sessions.isEmpty()) {
            log.info("过期 ACTIVE 会话扫描完成 scanned={}, closed={}, skippedActiveOrders={}",
                    sessions.size(), closed, skipped);
        }
        return closed;
    }

    /**
     * 补救扫描：EXPIRED 会话仍占用桌台（旧缺陷遗留），补记 CLOSED 并释放桌台。
     * 有进行中订单的跳过，避免误关仍在点餐的桌台。
     */
    private int repairLegacyExpiredSessions() {
        List<Long> sessionIds = tableSessionMapper.selectExpiredOccupiedSessionIds(BATCH_SIZE);
        int repaired = 0;
        for (Long sessionId : sessionIds) {
            TableSession session = tableSessionMapper.selectById(sessionId);
            if (session == null || session.getTableId() == null) {
                continue;
            }
            if (orderLookupPort.hasActiveOrderForSession(sessionId)) {
                continue;
            }
            try {
                Boolean released = transactionTemplate.execute(status -> tableReleaseService.releaseExpired(
                        session.getTableId(), sessionId));
                if (Boolean.TRUE.equals(released)) {
                    repaired++;
                }
            } catch (RuntimeException e) {
                log.warn("过期会话补救释放失败 sessionId={}, tableId={}, reason={}",
                        sessionId, session.getTableId(), e.getMessage());
            }
        }
        if (repaired > 0) {
            log.info("历史 EXPIRED 会话补救完成 repaired={}", repaired);
        }
        return repaired;
    }
}
