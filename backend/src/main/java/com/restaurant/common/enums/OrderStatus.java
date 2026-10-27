package com.restaurant.common.enums;

import java.util.List;

/**
 * 订单状态枚举（{@code orders.status}）：两套状态机共用同一状态集合。
 *
 * <p>外卖：PENDING_PAYMENT → PAID → ACCEPTED → MAKING → DELIVERING → COMPLETED；
 * 堂食：PENDING_PAYMENT → PAID → ACCEPTED → MAKING → SERVED → COMPLETED；
 * 终态：COMPLETED / CANCELLED / REJECTED。
 */
public enum OrderStatus {

    PENDING_PAYMENT("待支付"),
    PAID("待接单"),
    ACCEPTED("已接单"),
    MAKING("制作中"),
    DELIVERING("配送中"),
    SERVED("已出餐"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    REJECTED("已拒单");

    private final String text;

    OrderStatus(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static String textOf(String status) {
        for (OrderStatus value : values()) {
            if (value.name().equals(status)) {
                return value.text;
            }
        }
        return status;
    }

    public static boolean isValid(String status) {
        if (status == null) {
            return false;
        }
        for (OrderStatus value : values()) {
            if (value.name().equals(status)) {
                return true;
            }
        }
        return false;
    }

    /** 外卖进行中状态集合。 */
    public static List<String> takeoutActiveStatuses() {
        return List.of(PENDING_PAYMENT.name(), PAID.name(), ACCEPTED.name(), MAKING.name(), DELIVERING.name());
    }

    /** 堂食进行中状态集合。 */
    public static List<String> dineInActiveStatuses() {
        return List.of(PENDING_PAYMENT.name(), PAID.name(), ACCEPTED.name(), MAKING.name(), SERVED.name());
    }

    /** 已完成（终态）集合。 */
    public static List<String> finishedStatuses() {
        return List.of(COMPLETED.name(), CANCELLED.name(), REJECTED.name());
    }
}
