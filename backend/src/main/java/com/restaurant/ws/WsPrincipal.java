package com.restaurant.ws;

import java.security.Principal;

/**
 * STOMP 会话认证主体。
 *
 * <p>在 CONNECT 帧通过 JWT 校验后写入 {@code StompHeaderAccessor.setUser}，
 * 后续 SUBSCRIBE 帧的订阅 ACL（{@link WebSocketAuthChannelInterceptor}）据此判断
 * 用户身份、角色与所属商家。
 *
 * @param userId     users.id
 * @param username   登录名
 * @param role       ADMIN / MERCHANT / CUSTOMER
 * @param merchantId 商家账号所属 merchant.id（CUSTOMER/ADMIN 为 null）
 */
public record WsPrincipal(Long userId, String username, String role, Long merchantId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
