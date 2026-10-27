package com.restaurant.table.vo;

import java.time.LocalDateTime;

/** 堂食会话视图（{@code docs/02-api.md §2.7}）。 */
public record DineInSessionView(Long tableId,
                                String tableNo,
                                String tableName,
                                Long merchantId,
                                String merchantName,
                                Integer capacity,
                                Long sessionId,
                                String sessionToken,
                                String status,
                                LocalDateTime openedAt,
                                LocalDateTime expireAt,
                                Long currentOrderId,
                                String tableStatus) {
}
