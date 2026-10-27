package com.restaurant.table.vo;

import java.time.LocalDateTime;

/** 关台结果（{@code docs/02-api.md §10.4}）。 */
public record SessionClosedView(boolean closed, Long tableId, Long sessionId, LocalDateTime closedAt) {
}
