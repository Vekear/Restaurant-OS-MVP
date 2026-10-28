package com.restaurant.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.admin.dto.AdminUserStatusRequest;
import com.restaurant.admin.vo.AdminUserView;
import com.restaurant.audit.AuditActions;
import com.restaurant.audit.AuditService;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.enums.Role;
import com.restaurant.common.enums.UserStatus;
import com.restaurant.common.exception.BizException;
import com.restaurant.merchant.Merchant;
import com.restaurant.merchant.mapper.MerchantMapper;
import com.restaurant.security.RoleGuard;
import com.restaurant.user.User;
import com.restaurant.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理后台用户管理（{@code docs/02-api.md §20.1–20.2}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;

    private final MerchantMapper merchantMapper;

    private final RoleGuard roleGuard;

    private final AuditService auditService;

    public PageResult<AdminUserView> list(String keyword, String role, String status, int page, int size) {
        validateRole(role);
        validateStatus(status);
        var query = Wrappers.<User>lambdaQuery()
                .eq(role != null && !role.isBlank(), User::getRole, role)
                .eq(status != null && !status.isBlank(), User::getStatus, status)
                .orderByDesc(User::getCreatedAt)
                .orderByDesc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            if (value.length() > 30) {
                throw new BizException(ErrorCode.PARAM_INVALID, "keyword 不能超过 30 字");
            }
            query.and(w -> w.like(User::getUsername, value)
                    .or().like(User::getNickname, value)
                    .or().like(User::getPhone, value));
        }
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), query);
        Map<Long, Long> merchantIds = merchantIds(result.getRecords());
        List<AdminUserView> views = result.getRecords().stream()
                .map(user -> toView(user, merchantIds.get(user.getId())))
                .toList();
        return PageResult.of(views, result.getTotal(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserView updateStatus(Long id, AdminUserStatusRequest request) {
        Long currentUserId = roleGuard.currentUserId();
        if (currentUserId.equals(id)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "不能修改自己的账号状态");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!request.getStatus().equals(user.getStatus())) {
            user.setStatus(request.getStatus());
            int rows = userMapper.updateById(user);
            if (rows == 0) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "用户状态已变更，请刷新后重试");
            }
            String detail = request.getReason() == null || request.getReason().isBlank()
                    ? "状态变更为 " + request.getStatus()
                    : request.getReason().trim();
            auditService.event(roleGuard.currentUser(), AuditActions.USER_STATUS_CHANGE, "USER",
                    String.valueOf(id), detail, true);
            log.info("用户状态已变更 userId={}, status={}", id, request.getStatus());
        }
        Long merchantId = merchantIds(List.of(user)).get(user.getId());
        return toView(user, merchantId);
    }

    private Map<Long, Long> merchantIds(List<User> users) {
        List<Long> merchantUserIds = users.stream()
                .filter(user -> Role.MERCHANT.name().equals(user.getRole()))
                .map(User::getId)
                .toList();
        Map<Long, Long> result = new HashMap<>();
        if (merchantUserIds.isEmpty()) {
            return result;
        }
        for (Merchant merchant : merchantMapper.selectList(Wrappers.<Merchant>lambdaQuery()
                .in(Merchant::getUserId, merchantUserIds))) {
            result.put(merchant.getUserId(), merchant.getId());
        }
        return result;
    }

    private AdminUserView toView(User user, Long merchantId) {
        return new AdminUserView(user.getId(), user.getUsername(), user.getNickname(), maskPhone(user.getPhone()),
                user.getRole(), user.getStatus(), merchantId, user.getLastLoginAt(), user.getCreatedAt());
    }

    private static void validateRole(String role) {
        if (role != null && !role.isBlank()) {
            try {
                Role.of(role);
            } catch (IllegalArgumentException exception) {
                throw new BizException(ErrorCode.PARAM_INVALID, "非法的角色");
            }
        }
    }

    private static void validateStatus(String status) {
        if (status != null && !status.isBlank()
                && !UserStatus.ENABLED.name().equals(status) && !UserStatus.DISABLED.name().equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的用户状态");
        }
    }

    /** 手机号脱敏：138****8000。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
