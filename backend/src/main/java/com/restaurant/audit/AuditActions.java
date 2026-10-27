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

    /** 创建桌台。 */
    public static final String TABLE_CREATE = "TABLE_CREATE";

    /** 更新桌台。 */
    public static final String TABLE_UPDATE = "TABLE_UPDATE";

    /** 删除桌台。 */
    public static final String TABLE_DELETE = "TABLE_DELETE";

    /** 重签桌台二维码。 */
    public static final String TABLE_QR_REGENERATE = "TABLE_QR_REGENERATE";

    /** 扫码开桌。 */
    public static final String TABLE_SCAN_BIND = "TABLE_SCAN_BIND";

    /** 手输桌号开桌。 */
    public static final String TABLE_MANUAL_BIND = "TABLE_MANUAL_BIND";

    /** 桌台二维码校验失败。 */
    public static final String TABLE_QR_INVALID = "TABLE_QR_INVALID";

    /** 关台（顾客主动结束会话）。 */
    public static final String TABLE_CLOSE = "TABLE_CLOSE";

    /** 商家资料更新。 */
    public static final String MERCHANT_PROFILE_UPDATE = "MERCHANT_PROFILE_UPDATE";

    /** 营业状态切换。 */
    public static final String BUSINESS_STATUS_CHANGE = "BUSINESS_STATUS_CHANGE";

    /** 商家审核。 */
    public static final String MERCHANT_AUDIT = "MERCHANT_AUDIT";

    /** 商家启用/禁用。 */
    public static final String MERCHANT_STATUS_CHANGE = "MERCHANT_STATUS_CHANGE";

    /** 用户启用/禁用。 */
    public static final String USER_STATUS_CHANGE = "USER_STATUS_CHANGE";

    /** 优惠券创建。 */
    public static final String COUPON_CREATE = "COUPON_CREATE";

    /** 优惠券更新。 */
    public static final String COUPON_UPDATE = "COUPON_UPDATE";

    /** 优惠券删除。 */
    public static final String COUPON_DELETE = "COUPON_DELETE";

    /** 优惠券状态切换。 */
    public static final String COUPON_STATUS_CHANGE = "COUPON_STATUS_CHANGE";

    private AuditActions() {
    }
}
