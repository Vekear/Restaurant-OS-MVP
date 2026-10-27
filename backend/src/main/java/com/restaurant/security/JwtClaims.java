package com.restaurant.security;

import java.time.Instant;

/**
 * JWT 解析结果（不可变）。
 *
 * @param userId     users.id
 * @param username   登录账号
 * @param role       角色名 ADMIN/MERCHANT/CUSTOMER
 * @param merchantId 商家 ID（仅 MERCHANT 有值）
 * @param jti        token 唯一标识，登出黑名单以它为 key
 * @param issuedAt   签发时间
 * @param expiresAt  过期时间
 */
public record JwtClaims(Long userId,
                        String username,
                        String role,
                        Long merchantId,
                        String jti,
                        Instant issuedAt,
                        Instant expiresAt) {
}
