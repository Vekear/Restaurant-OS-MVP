package com.restaurant.contract;

import java.time.LocalDateTime;

/**
 * 跨域端口：桌台会话（实现方：table 域；消费方：order 域堂食下单/查询）。
 *
 * <p>堂食订单的商家、桌号、会话全部来自已验证的会话快照，避免客户端伪造桌台信息。
 */
public interface TableSessionPort {

    /**
     * 校验会话 token 并返回快照（会话必须 ACTIVE 且未过期）。
     *
     * @param sessionToken 请求头 {@code X-Table-Session} 的值
     * @return 会话快照
     */
    TableSessionSnapshot requireActive(String sessionToken);

    /**
     * 查询用户当前 ACTIVE 会话（无则返回 {@code null}）。
     *
     * @param userId 用户 ID
     * @return 最近一次开桌的会话快照
     */
    TableSessionSnapshot findActiveByUser(Long userId);

    /**
     * 记录会话当前进行中订单（用于超时清理时判断“无进行中订单”）。
     *
     * @param sessionId 会话 ID
     * @param orderId   订单 ID
     */
    void touchOrder(Long sessionId, Long orderId);

    /**
     * 会话快照。
     *
     * @param sessionId    会话 ID
     * @param tableId      桌台 ID
     * @param tableNo      桌号
     * @param tableName    桌台名称
     * @param merchantId   商家 ID
     * @param merchantName 商家名称
     * @param capacity     容纳人数
     * @param sessionToken 会话 token
     * @param status       会话状态
     * @param openedAt     开桌时间
     * @param expireAt     过期时间
     */
    record TableSessionSnapshot(Long sessionId,
                                Long tableId,
                                String tableNo,
                                String tableName,
                                Long merchantId,
                                String merchantName,
                                Integer capacity,
                                String sessionToken,
                                String status,
                                LocalDateTime openedAt,
                                LocalDateTime expireAt) {
    }
}
