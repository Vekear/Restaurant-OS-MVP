package com.restaurant.order.vo;

import java.time.LocalDateTime;

/** 订单状态时间线项。 */
public record StatusLogView(String fromStatus, String toStatus, String operatorRole, Long operatorId,
                            String remark, LocalDateTime createdAt) {
}
