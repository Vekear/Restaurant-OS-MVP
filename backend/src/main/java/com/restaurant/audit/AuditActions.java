package com.restaurant.audit;

/**
 * 审计动作常量（写入 {@code audit_log.action}）。
 *
 * <p>命名与 {@code docs/01-architecture.md §16.8} 一致，后续任务追加订单/桌台/菜单动作时在此扩展。
 */
public final class AuditActions {

    /** 登录成功。 */
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";

    /** 登录失败（用户名不存在/密码错误/账号禁用/微信 code 无效）。 */
    public static final String LOGIN_FAIL = "LOGIN_FAIL";

    /** 登出。 */
    public static final String LOGOUT = "LOGOUT";

    /** 修改个人资料。 */
    public static final String PROFILE_UPDATE = "PROFILE_UPDATE";

    private AuditActions() {
    }
}
