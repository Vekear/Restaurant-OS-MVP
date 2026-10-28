package com.restaurant.admin.vo;

import java.time.LocalDateTime;

/** 管理后台用户视图（手机号脱敏）。 */
public record AdminUserView(Long id, String username, String nickname, String phone, String role,
                            String status, Long merchantId, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
}
