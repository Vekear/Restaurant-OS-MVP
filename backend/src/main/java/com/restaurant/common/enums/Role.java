package com.restaurant.common.enums;

/**
 * 用户角色（与 {@code users.role} 列、JWT {@code role} claim、Spring Security authority 一一对应）。
 *
 * <p>authority 固定为 {@code ROLE_<name>}，SecurityConfig 使用 {@code hasRole("ADMIN")} 等表达式。
 */
public enum Role {

    /** 平台管理员：管理后台全部接口。 */
    ADMIN,

    /** 商家：仅能操作自己名下店铺的菜单/订单/报表。 */
    MERCHANT,

    /** 顾客：外卖下单、堂食扫码点餐、评价。 */
    CUSTOMER;

    /** Spring Security authority 名称（{@code ROLE_ADMIN} 等）。 */
    public String authority() {
        return "ROLE_" + name();
    }

    /** 从数据库/JWT 中的字符串解析角色，非法值抛 {@link IllegalArgumentException}。 */
    public static Role of(String value) {
        if (value == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知角色: " + value, exception);
        }
    }
}
