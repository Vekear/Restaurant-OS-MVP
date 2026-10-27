package com.restaurant.auth.vo;

import com.restaurant.user.User;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 当前登录用户完整视图（{@code docs/02-api.md §4.5}）。
 *
 * <p>{@code GET /auth/me} 返回完整手机号；其他接口如需展示手机号应自行脱敏。
 */
@Value
public class UserView {

    Long id;

    String username;

    String nickname;

    String phone;

    String avatarUrl;

    String role;

    String status;

    Long merchantId;

    LocalDateTime createdAt;

    public static UserView from(User user, Long merchantId) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(),
                user.getAvatarUrl(), user.getRole(), user.getStatus(), merchantId, user.getCreatedAt());
    }
}
