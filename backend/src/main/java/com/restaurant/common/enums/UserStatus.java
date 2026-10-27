package com.restaurant.common.enums;

/**
 * 用户状态（{@code users.status} 列，CHECK 约束 ENABLED/DISABLED）。
 *
 * <p>DISABLED 用户登录返回 40304；已签发 token 在校验时也会因状态被拒绝。
 */
public enum UserStatus {

    ENABLED,

    DISABLED;

    /** 判断字符串是否为启用状态。 */
    public static boolean isEnabled(String status) {
        return ENABLED.name().equals(status);
    }
}
