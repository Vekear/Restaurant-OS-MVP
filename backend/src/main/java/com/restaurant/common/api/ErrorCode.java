package com.restaurant.common.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码注册表（与 {@code docs/01-architecture.md §16.6} 一一对应）。
 *
 * <p>编码规则：{@code HTTP 状态码 × 100 + 两位序号}，例如 {@code 40901 = 409 + 01}。
 * 前端按 {@code code} 分支，不解析 {@code message}；新增/调整错误码必须先改架构文档再改本枚举。
 */
@Getter
public enum ErrorCode {

    // ---------- 成功 ----------
    SUCCESS(0, "ok", HttpStatus.OK),

    // ---------- 400 客户端参数 ----------
    PARAM_INVALID(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_INVALID(40002, "缺少或非法 Idempotency-Key", HttpStatus.BAD_REQUEST),
    FILE_INVALID(40003, "文件类型或大小不合法", HttpStatus.BAD_REQUEST),
    TABLE_QR_INVALID(40004, "桌台二维码签名无效", HttpStatus.BAD_REQUEST),
    AMOUNT_MISMATCH(40005, "金额不一致", HttpStatus.BAD_REQUEST),

    // ---------- 401 认证 ----------
    UNAUTHENTICATED(40101, "未认证或凭据无效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "登录凭证已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(40103, "登录凭证无效", HttpStatus.UNAUTHORIZED),

    // ---------- 403 鉴权 ----------
    ROLE_FORBIDDEN(40301, "角色无权限", HttpStatus.FORBIDDEN),
    RESOURCE_FORBIDDEN(40302, "资源越权", HttpStatus.FORBIDDEN),
    MERCHANT_UNAVAILABLE(40303, "商家不可用", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(40304, "账号已被禁用", HttpStatus.FORBIDDEN),

    // ---------- 404 资源 ----------
    NOT_FOUND(40401, "资源不存在", HttpStatus.NOT_FOUND),

    // ---------- 409 状态 / 并发冲突 ----------
    STOCK_INSUFFICIENT(40901, "库存不足", HttpStatus.CONFLICT),
    CART_FULL(40902, "购物车已满", HttpStatus.CONFLICT),
    ADDRESS_LIMIT_EXCEEDED(40903, "地址数量超限", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(40904, "幂等键冲突", HttpStatus.CONFLICT),
    ORDER_STATUS_INVALID(40905, "订单状态不允许该操作", HttpStatus.CONFLICT),
    ORDER_ALREADY_REVIEWED(40906, "该订单已评价", HttpStatus.CONFLICT),
    ORDER_NOT_COMPLETED(40907, "订单未完成不可评价", HttpStatus.CONFLICT),
    MERCHANT_CLOSED(40908, "商家休息中", HttpStatus.CONFLICT),
    TABLE_SESSION_ACTIVE(40909, "桌台存在进行中会话", HttpStatus.CONFLICT),
    TABLE_OCCUPIED(40910, "桌台已被占用", HttpStatus.CONFLICT),
    TABLE_SESSION_INVALID(40911, "桌台会话无效或已关闭", HttpStatus.CONFLICT),
    COUPON_UNAVAILABLE(40912, "优惠券不可用", HttpStatus.CONFLICT),
    PAYMENT_ALREADY_PAID(40913, "支付单已支付", HttpStatus.CONFLICT),
    UNIQUE_CONFLICT(40914, "唯一约束冲突", HttpStatus.CONFLICT),
    CATEGORY_NOT_EMPTY(40915, "分类下仍有菜品", HttpStatus.CONFLICT),
    SESSION_HAS_ACTIVE_ORDER(40916, "会话存在进行中订单", HttpStatus.CONFLICT),
    USER_HAS_ACTIVE_SESSION(40917, "用户已有其他进行中会话", HttpStatus.CONFLICT),
    STATE_CONFLICT(40918, "资源状态冲突", HttpStatus.CONFLICT),

    // ---------- 429 限流 ----------
    TOO_MANY_REQUESTS(42901, "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),

    // ---------- 500 服务端 ----------
    INTERNAL_ERROR(50001, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_ERROR(50002, "文件存储不可用", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;

    private final String message;

    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
