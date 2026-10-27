package com.restaurant.contract;

/** 跨域端口：用户展示信息查询（实现方：user 域）。 */
public interface UserLookupPort {

    /**
     * 读取用户昵称。
     *
     * @param userId 用户 ID
     * @return 不存在返回 {@code null}
     */
    String nickname(Long userId);
}
