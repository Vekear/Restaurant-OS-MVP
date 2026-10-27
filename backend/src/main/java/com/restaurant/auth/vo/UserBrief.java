package com.restaurant.auth.vo;

import com.restaurant.user.User;
import lombok.Value;

/**
 * 冗余在 token 响应中的用户摘要（{@code docs/02-api.md §4.1}）。
 *
 * <p>不包含 password / phone 等敏感字段；merchantId 仅 MERCHANT 有值。
 */
@Value
public class UserBrief {

    Long id;

    String username;

    String nickname;

    String role;

    Long merchantId;

    String status;

    public static UserBrief from(User user, Long merchantId) {
        return new UserBrief(user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), merchantId, user.getStatus());
    }
}
