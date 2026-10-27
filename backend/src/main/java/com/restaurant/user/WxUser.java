package com.restaurant.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 微信小程序用户表 {@code wx_user}。
 *
 * <p>{@code session_key} 仅落库用于服务端解密，绝不写入响应/JWT/日志；
 * 同一 openid 全局唯一，首次登录时自动关联 {@link User}。
 */
@Getter
@Setter
@TableName("wx_user")
public class WxUser extends BaseEntity {

    private String openid;

    private String unionid;

    private String sessionKey;

    private String nickname;

    private String avatarUrl;

    private Long userId;

    @Version
    private Integer version;
}
