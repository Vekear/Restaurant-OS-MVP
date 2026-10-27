package com.restaurant.common.enums;

/** 列表查询状态分组（{@code OrderStatusGroup}）。 */
public enum OrderStatusGroup {

    ALL,
    ACTIVE,
    FINISHED;

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (OrderStatusGroup group : values()) {
            if (group.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
