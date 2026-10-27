package com.restaurant.common.enums;

/** 订单类型枚举（{@code orders.order_type}）。 */
public enum OrderType {

    /** 外卖订单。 */
    TAKEOUT,

    /** 堂食订单（T09 实现下单主流程）。 */
    DINE_IN;

    /** 是否为合法取值。 */
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (OrderType type : values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
