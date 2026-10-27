package com.restaurant.auth.vo;

import lombok.Value;

/**
 * 登录/注册/微信登录统一响应体（{@code docs/02-api.md §4.1}）。
 *
 * <p>tokenType 固定 Bearer；expiresIn 为秒；user 为安全摘要。
 */
@Value
public class AuthTokenView {

    String token;

    String tokenType;

    long expiresIn;

    UserBrief user;

    public static AuthTokenView of(String token, long expiresIn, UserBrief user) {
        return new AuthTokenView(token, "Bearer", expiresIn, user);
    }
}
