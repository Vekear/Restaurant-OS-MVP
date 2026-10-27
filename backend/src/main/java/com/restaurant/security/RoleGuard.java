package com.restaurant.security;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.enums.Role;
import com.restaurant.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 业务层角色守卫（{@code @PreAuthorize} 之外的命令式校验入口）。
 *
 * <p>用于 Service 内需要显式取当前用户/商家 ID 的场景，避免到处重复 SecurityContextHolder 样板代码。
 * 认证失败抛 40101，角色不符抛 40301，缺 merchantId 抛 40301。
 */
@Component
@RequiredArgsConstructor
public class RoleGuard {

    /** 当前登录用户，未认证抛 40101。 */
    public UserPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    /** 当前用户 ID，未认证抛 40101。 */
    public Long currentUserId() {
        return currentUser().getId();
    }

    /** 要求指定角色之一，否则抛 40301。 */
    public UserPrincipal requireAnyRole(Role... roles) {
        UserPrincipal principal = currentUser();
        boolean matched = Arrays.stream(roles).anyMatch(role -> role.name().equals(principal.getRole()));
        if (!matched) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN);
        }
        return principal;
    }

    /** 要求 MERCHANT 角色并返回其店铺 ID。 */
    public Long requireMerchantId() {
        UserPrincipal principal = requireAnyRole(Role.MERCHANT);
        Long merchantId = principal.getMerchantId();
        if (merchantId == null) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "当前商家账号未绑定店铺");
        }
        return merchantId;
    }
}
