package com.restaurant.admin.vo;

import java.time.LocalDateTime;

/** 审计日志视图（{@code docs/02-api.md §2.9 AuditLogView}）。 */
public record AuditLogView(Long id, Long userId, String username, String role, String action,
                           String targetType, String targetId, String detail, String ip, String userAgent,
                           Boolean success, String traceId, LocalDateTime createdAt) {
}
