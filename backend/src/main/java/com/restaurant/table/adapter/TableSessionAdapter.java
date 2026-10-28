package com.restaurant.table.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.MerchantPort;
import com.restaurant.contract.TableSessionPort;
import com.restaurant.table.TableInfo;
import com.restaurant.table.service.TableReleaseService;
import com.restaurant.table.TableSession;
import com.restaurant.table.mapper.TableInfoMapper;
import com.restaurant.table.mapper.TableSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** {@link TableSessionPort} 的桌台域实现（堂食下单/查询读取会话）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableSessionAdapter implements TableSessionPort {

    private final TableSessionMapper tableSessionMapper;

    private final TableInfoMapper tableInfoMapper;

    private final MerchantPort merchantPort;

    private final TableReleaseService tableReleaseService;

    @Override
    public TableSessionSnapshot requireActive(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            throw new BizException(ErrorCode.TABLE_SESSION_INVALID, "桌台会话缺失，请先扫码开桌");
        }
        TableSession session = tableSessionMapper.selectOne(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getSessionToken, sessionToken));
        if (session == null || !"ACTIVE".equals(session.getStatus())) {
            throw new BizException(ErrorCode.TABLE_SESSION_INVALID, "桌台会话无效或已关闭，请重新扫码");
        }
        if (session.getExpireAt() != null && session.getExpireAt().isBefore(LocalDateTime.now())) {
            expireAndRelease(session);
            throw new BizException(ErrorCode.TABLE_SESSION_INVALID, "桌台会话已过期，请重新扫码");
        }
        return snapshot(session);
    }

    @Override
    public TableSessionSnapshot findActiveByUser(Long userId) {
        if (userId == null) {
            return null;
        }
        TableSession session = tableSessionMapper.selectOne(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getStatus, "ACTIVE")
                .eq(TableSession::getOpenedBy, String.valueOf(userId))
                .orderByDesc(TableSession::getOpenedAt)
                .last("LIMIT 1"));
        if (session == null) {
            return null;
        }
        if (session.getExpireAt() != null && session.getExpireAt().isBefore(LocalDateTime.now())) {
            expireAndRelease(session);
            return null;
        }
        return snapshot(session);
    }

    @Override
    public void touchOrder(Long sessionId, Long orderId) {
        tableSessionMapper.touchOrder(sessionId, orderId);
    }

    /**
     * 惰性过期：不能只把会话置 EXPIRED，必须同时释放桌台，否则桌台长期 OCCUPIED，
     * 重新扫码会得到 40910（T31 缺口）。释放失败时回退为仅标记过期，由清理任务补救。
     */
    private void expireAndRelease(TableSession session) {
        try {
            tableReleaseService.releaseExpired(session.getTableId(), session.getId());
        } catch (RuntimeException e) {
            log.warn("过期桌台会话释放失败，回退为仅标记过期 sessionId={}, tableId={}, reason={}",
                    session.getId(), session.getTableId(), e.getMessage());
            try {
                tableSessionMapper.expire(session.getId());
            } catch (RuntimeException retry) {
                log.warn("会话过期标记失败 sessionId={}, reason={}", session.getId(), retry.getMessage());
            }
        }
    }

    private TableSessionSnapshot snapshot(TableSession session) {
        TableInfo table = tableInfoMapper.selectById(session.getTableId());
        MerchantPort.MerchantSnapshot merchant = table == null ? null : merchantPort.get(table.getMerchantId());
        return new TableSessionSnapshot(session.getId(),
                session.getTableId(),
                table == null ? null : table.getTableNo(),
                table == null ? null : table.getName(),
                session.getMerchantId(),
                merchant == null ? null : merchant.name(),
                table == null ? null : table.getCapacity(),
                session.getSessionToken(),
                session.getStatus(),
                session.getOpenedAt(),
                session.getExpireAt());
    }
}
