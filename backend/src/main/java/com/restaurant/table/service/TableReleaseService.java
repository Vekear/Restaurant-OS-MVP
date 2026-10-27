package com.restaurant.table.service;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
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
 * <p>订单完成/取消、用户关台、会话清理均在各自事务内调用，保证
 * “订单状态 + 会话状态 + 桌台状态”三写一致（{@code docs/02-api.md §18.12}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableReleaseService implements TableReleasePort {

    private final TableInfoMapper tableInfoMapper;

    private final TableSessionMapper tableSessionMapper;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long tableId, Long sessionId) {
        TableSession session = tableSessionMapper.selectById(sessionId);
        TableInfo table = tableInfoMapper.selectById(tableId);
        if (table == null) {
            closeIfActive(session);
            return;
        }
        if ("FREE".equals(table.getStatus()) && table.getCurrentSessionId() == null) {
            closeIfActive(session);
            return;
        }
        if (!Objects.equals(table.getCurrentSessionId(), sessionId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "桌台会话不匹配，请刷新后重试");
        }
        closeIfActive(session);
        int released = tableInfoMapper.release(tableId, sessionId, table.getVersion());
        if (released == 0) {
            TableInfo latest = tableInfoMapper.selectById(tableId);
            if (latest == null || !"FREE".equals(latest.getStatus()) || latest.getCurrentSessionId() != null) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "桌台状态已变更，请刷新后重试");
            }
        }
        eventPublisher.publishEvent(new TableSessionClosedEvent(table.getId(), table.getTableNo(),
                table.getMerchantId(), sessionId, "CLOSED", LocalDateTime.now(),
                TraceContext.currentTraceId()));
        log.info("桌台已释放 tableId={}, tableNo={}, sessionId={}", tableId, table.getTableNo(), sessionId);
    }

    private void closeIfActive(TableSession session) {
        if (session == null || !"ACTIVE".equals(session.getStatus())) {
            return;
        }
        if (tableSessionMapper.close(session.getId(), session.getVersion()) == 0) {
            TableSession latest = tableSessionMapper.selectById(session.getId());
            if (latest != null && "ACTIVE".equals(latest.getStatus())) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "会话状态已变更，请刷新后重试");
            }
        }
    }
}
