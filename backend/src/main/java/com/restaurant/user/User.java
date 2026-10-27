package com.restaurant.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户表 {@code users}（ADMIN / MERCHANT / CUSTOMER 共用）。
 *
 * <p>密码列只存 BCrypt 哈希；任何响应 VO 都不得包含 password。
 */
@Getter
@Setter
@TableName("users")
public class User extends BaseEntity {

    private String username;

    /** BCrypt 哈希，cost 10。 */
    private String password;

    private String nickname;

    private String phone;

    private String avatarUrl;

    /** {@link com.restaurant.common.enums.Role} 名称。 */
    private String role;

    /** {@link com.restaurant.common.enums.UserStatus} 名称。 */
    private String status;

    private LocalDateTime lastLoginAt;

    @Version
    private Integer version;
}
