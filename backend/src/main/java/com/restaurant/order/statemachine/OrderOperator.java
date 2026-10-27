package com.restaurant.order.statemachine;

import com.restaurant.common.enums.OperatorRole;

/**
 * 状态机操作者。
 *
 * @param id   CUSTOMER/ADMIN 为用户 ID；MERCHANT 为商家 ID；SYSTEM 为 0
 * @param role 操作者角色
 */
public record OrderOperator(Long id, OperatorRole role) {

    public static OrderOperator customer(Long userId) {
        return new OrderOperator(userId, OperatorRole.CUSTOMER);
    }

    public static OrderOperator merchant(Long merchantId) {
        return new OrderOperator(merchantId, OperatorRole.MERCHANT);
    }

    public static OrderOperator system() {
        return new OrderOperator(0L, OperatorRole.SYSTEM);
    }

    public static OrderOperator admin(Long userId) {
        return new OrderOperator(userId, OperatorRole.ADMIN);
    }
}
