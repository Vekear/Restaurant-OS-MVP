package com.restaurant.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 认证主体：JWT 校验通过后放入 SecurityContext。
 *
 * <p>携带 jti / expiresAt 供登出黑名单使用；merchantId 仅 MERCHANT 有值，
 * 商家数据隔离（后续任务）直接从该字段取店铺 ID。
 */
@Getter
public class UserPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;

    private final String username;

    private final String nickname;

    private final String role;

    private final Long merchantId;

    private final String status;

    private final String jti;

    private final Instant expiresAt;

    public UserPrincipal(Long id,
                         String username,
                         String nickname,
                         String role,
                         Long merchantId,
                         String status,
                         String jti,
                         Instant expiresAt) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.merchantId = merchantId;
        this.status = status;
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ENABLED".equals(status);
    }
}
