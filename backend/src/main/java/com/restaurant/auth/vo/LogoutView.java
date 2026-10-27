package com.restaurant.auth.vo;

import lombok.Value;

/** 登出响应（{@code docs/02-api.md §4.5}）：重复登出同样返回 loggedOut=true。 */
@Value
public class LogoutView {

    boolean loggedOut;

    public static LogoutView success() {
        return new LogoutView(true);
    }
}
