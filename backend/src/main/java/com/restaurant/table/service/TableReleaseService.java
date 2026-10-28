package com.restaurant.table.service;

import com.restaurant.common.log.TraceContext;
import com.restaurant.contract.TableReleasePort;
import com.restaurant.contract.event.TableSessionClosedEvent;
import com.restaurant.table.TableInfo;
import com.restaurant.table.TableSession;
import com.restaurant.table.mapper.TableInfoMapper;
import com.restaurant.table.mapper.TableSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 释放桌台（{@link TableReleasePort} 实现）：关闭会话 + 桌台置 FREE，操作幂等。
 *
 * <p>订单完成/取消、用户关台、会话过期（惰性判定与定时清理）均在各自事务内调用，
 * 保证“订单状态 + 会话状态 + 桌台状态”一致（{@code docs/02-api.md §18.12}）。
 *
 * <p>并发处理：全部使用条件 UPDATE（乐观锁），不做裸更新；更新 0 行时重读最新状态，
 * 若已被其他事务关闭/释放则按幂等处理（最多重试 {@value #MAX_ATTEMPTS} 次），
 * 桌台若已被其他会话接管则跳过释放，避免误释放新客人的桌台。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableReleaseService implements TableReleasePort {

    private static final int MAX_ATTEMPTS = 3;

    private static final String REASON_CLOSED = "CLOSED";

    private static final String REASON_EXPIRED = "EXPIRED";

    private final TableInfoMapper tableInfoMapper;

    private final TableSessionMapper tableSessionMapper;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long tableId, Long sessionId) {
        releaseInternal(tableId, sessionId, REASON_CLOSED);
    }

    /**
     * 会话过期释放（惰性判定与清理任务共用）：会话 → CLOSED、桌台 → FREE 并发布
     * {@link TableSessionClosedEvent}（reason=EXPIRED），与状态变更在同一事务内完成。
     *
     * <p>同时兼容旧版本缺陷遗留的 {@code EXPIRED} 会话：仍占用桌台时补记为 CLOSED 并释放桌台。
     *
     * @param tableId   桌台 ID
     * @param sessionId 会话 ID
     * @return 本次是否真正发生了状态变更（幂等调用返回 {@code false}）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean releaseExpired(Long tableId, Long sessionId) {
        return releaseInternal(tableId, sessionId, REASON_EXPIRED);
    }

    private boolean releaseInternal(Long tableId, Long sessionId, String reason) {
        TableSession session = tableSessionMapper.selectById(sessionId);
        TableInfo table = tableInfoMapper.selectById(tableId);
        boolean sessionClosed = closeSession(session, reason);
        boolean tableReleased = table != null && releaseTable(table, sessionId);
        if (!sessionClosed && !tableReleased) {
            return false;
        }
        Long merchantId = table != null ? table.getMerchantId()
                : session == null ? null : session.getMerchantId();
        eventPublisher.publishEvent(new TableSessionClosedEvent(tableId,
                table == null ? null : table.getTableNo(), merchantId, sessionId, reason,
                LocalDateTime.now(), TraceContext.currentTraceId()));
        log.info("桌台会话已释放 tableId={}, tableNo={}, sessionId={}, reason={}, sessionClosed={}, tableReleased={}",
                tableId, table == null ? null : table.getTableNo(), sessionId, reason,
                sessionClosed, tableReleased);
        return true;
    }

    /** 会话 ACTIVE/EXPIRED → CLOSED；已 CLOSED 视为已释放（幂等）。 */
    private boolean closeSession(TableSession session, String reason) {
        if (session == null || session.getId() == null) {
            return false;
        }
        Long sessionId = session.getId();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            TableSession current = attempt == 1 ? session : tableSessionMapper.selectById(sessionId);
            if (current == null) {
                return false;
            }
            String status = current.getStatus();
            if ("CLOSED".equals(status)) {
                return false;
            }
            int updated;
            if ("ACTIVE".equals(status)) {
                updated = tableSessionMapper.close(sessionId, current.getVersion());
            } else if ("EXPIRED".equals(status) && REASON_EXPIRED.equals(reason)) {
                updated = tableSessionMapper.closeExpired(sessionId);
            } else {
                return false;
            }
            if (updated > 0) {
                return true;
            }
            log.warn("会话关闭乐观锁冲突，重试 sessionId={}, attempt={}", sessionId, attempt);
        }
        log.warn("会话关闭重试后仍未成功，按已关闭处理 sessionId={}, reason={}", sessionId, reason);
        return false;
    }

    /** 桌台 OCCUPIED 且仍由该会话占用时置 FREE；FREE/已被其他会话接管均视为已释放。 */
    private boolean releaseTable(TableInfo table, Long sessionId) {
        Long tableId = table.getId();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            TableInfo current = attempt == 1 ? table : tableInfoMapper.selectById(tableId);
            if (current == null) {
                return false;
            }
            if ("FREE".equals(current.getStatus()) && current.getCurrentSessionId() == null) {
                return false;
            }
            if (!Objects.equals(current.getCurrentSessionId(), sessionId)) {
                log.info("桌台已由其他会话占用，跳过释放 tableId={}, sessionId={}, currentSessionId={}",
                        tableId, sessionId, current.getCurrentSessionId());
                return false;
            }
            if (tableInfoMapper.release(tableId, sessionId, current.getVersion()) > 0) {
                return true;
            }
            log.warn("桌台释放乐观锁冲突，重试 tableId={}, sessionId={}, attempt={}", tableId, sessionId, attempt);
        }
        log.warn("桌台释放重试后仍未成功，按已释放处理 tableId={}, sessionId={}", tableId, sessionId);
        return false;
    }
}
