package com.restaurant.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.admin.vo.AuditLogView;
import com.restaurant.audit.AuditLog;
import com.restaurant.audit.mapper.AuditLogMapper;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.util.DateRanges;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 管理后台审计日志查询（{@code docs/02-api.md §20.20}）。 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AuditLogMapper auditLogMapper;

    @Transactional(readOnly = true)
    public PageResult<AuditLogView> list(String action, Long userId, String startDate, String endDate,
                                         int page, int size) {
        LocalDate start = DateRanges.parseOptional(startDate, "startDate");
        LocalDate end = DateRanges.parseOptional(endDate, "endDate");
        var query = Wrappers.<AuditLog>lambdaQuery()
                .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                .eq(userId != null, AuditLog::getUserId, userId)
                .ge(start != null, AuditLog::getCreatedAt, start == null ? null : DateRanges.startOf(start))
                .le(end != null, AuditLog::getCreatedAt, end == null ? null : DateRanges.endOf(end))
                .orderByDesc(AuditLog::getCreatedAt)
                .orderByDesc(AuditLog::getId);
        IPage<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, size), query);
        List<AuditLogView> views = result.getRecords().stream()
                .map(log -> new AuditLogView(log.getId(), log.getUserId(), log.getUsername(), log.getRole(),
                        log.getAction(), log.getTargetType(), log.getTargetId(), log.getDetail(), log.getIp(),
                        log.getUserAgent(), log.getSuccess(), log.getTraceId(), log.getCreatedAt()))
                .toList();
        return PageResult.of(views, result.getTotal(), page, size);
    }
}
