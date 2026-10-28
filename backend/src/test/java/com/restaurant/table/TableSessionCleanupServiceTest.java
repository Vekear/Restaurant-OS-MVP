package com.restaurant.table;

import com.restaurant.contract.OrderLookupPort;
import com.restaurant.table.mapper.TableSessionMapper;
import com.restaurant.table.service.TableReleaseService;
import com.restaurant.table.service.TableSessionCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TableSessionCleanupService} 单元测试：单桌异常隔离（WARN）与有进行中订单跳过。
 */
@ExtendWith(OutputCaptureExtension.class)
class TableSessionCleanupServiceTest {

    private TableSessionMapper tableSessionMapper;

    private OrderLookupPort orderLookupPort;

    private TableReleaseService tableReleaseService;

    private TableSessionCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        tableSessionMapper = mock(TableSessionMapper.class);
        orderLookupPort = mock(OrderLookupPort.class);
        tableReleaseService = mock(TableReleaseService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        cleanupService = new TableSessionCleanupService(tableSessionMapper, orderLookupPort,
                tableReleaseService, new TransactionTemplate(transactionManager));
        when(tableSessionMapper.selectExpiredOccupiedSessionIds(any(Integer.class))).thenReturn(List.of());
    }

    @Test
    void singleTableFailureIsLoggedAsWarnAndDoesNotBlockOthers(CapturedOutput output) {
        TableSession first = expiredSession(1L, 11L);
        TableSession second = expiredSession(2L, 22L);
        when(tableSessionMapper.selectList(any())).thenReturn(List.of(first, second));
        when(orderLookupPort.hasActiveOrderForSession(anyLong())).thenReturn(false);
        when(tableReleaseService.releaseExpired(11L, 1L)).thenThrow(new IllegalStateException("模拟死锁"));
        when(tableReleaseService.releaseExpired(22L, 2L)).thenReturn(true);

        int closed = cleanupService.cleanupExpiredSessions();

        assertThat(closed).isEqualTo(1);
        verify(tableReleaseService).releaseExpired(11L, 1L);
        verify(tableReleaseService).releaseExpired(22L, 2L);
        assertThat(output).contains("桌台会话清理失败").contains("sessionId=1").contains("模拟死锁");
    }

    @Test
    void sessionWithActiveOrderIsSkipped() {
        TableSession withOrder = expiredSession(3L, 33L);
        TableSession idle = expiredSession(4L, 44L);
        when(tableSessionMapper.selectList(any())).thenReturn(List.of(withOrder, idle));
        when(orderLookupPort.hasActiveOrderForSession(3L)).thenReturn(true);
        when(orderLookupPort.hasActiveOrderForSession(4L)).thenReturn(false);
        when(tableReleaseService.releaseExpired(44L, 4L)).thenReturn(true);

        int closed = cleanupService.cleanupExpiredSessions();

        assertThat(closed).isEqualTo(1);
        verify(tableReleaseService, times(1)).releaseExpired(eq(44L), eq(4L));
        verify(tableReleaseService, never()).releaseExpired(eq(33L), eq(3L));
    }

    @Test
    void idempotentReleaseIsNotCounted() {
        TableSession released = expiredSession(5L, 55L);
        when(tableSessionMapper.selectList(any())).thenReturn(List.of(released));
        when(orderLookupPort.hasActiveOrderForSession(5L)).thenReturn(false);
        when(tableReleaseService.releaseExpired(55L, 5L)).thenReturn(false);

        assertThat(cleanupService.cleanupExpiredSessions()).isZero();
    }

    private TableSession expiredSession(Long sessionId, Long tableId) {
        TableSession session = new TableSession();
        session.setId(sessionId);
        session.setTableId(tableId);
        session.setStatus("ACTIVE");
        session.setExpireAt(LocalDateTime.now().minusMinutes(10));
        session.setVersion(0);
        return session;
    }
}
