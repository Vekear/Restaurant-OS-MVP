package com.restaurant.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.admin.dto.MerchantAuditRequest;
import com.restaurant.audit.AuditActions;
import com.restaurant.audit.AuditService;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.exception.BizException;
import com.restaurant.merchant.Merchant;
import com.restaurant.merchant.mapper.MerchantMapper;
import com.restaurant.merchant.vo.MerchantAdminView;
import com.restaurant.order.Order;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.security.RoleGuard;
import com.restaurant.user.User;
import com.restaurant.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 管理后台商家管理（{@code docs/02-api.md §20.3–20.5}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMerchantService {

    private final MerchantMapper merchantMapper;

    private final UserMapper userMapper;

    private final OrderMapper orderMapper;

    private final RoleGuard roleGuard;

    private final AuditService auditService;

    public PageResult<MerchantAdminView> list(String keyword, String auditStatus, String status, int page, int size) {
        validateAuditStatus(auditStatus);
        validateMerchantStatus(status);
        var query = Wrappers.<Merchant>lambdaQuery()
                .eq(auditStatus != null && !auditStatus.isBlank(), Merchant::getAuditStatus, auditStatus)
                .eq(status != null && !status.isBlank(), Merchant::getStatus, status)
                .orderByDesc(Merchant::getCreatedAt)
                .orderByDesc(Merchant::getId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            if (value.length() > 30) {
                throw new BizException(ErrorCode.PARAM_INVALID, "keyword 不能超过 30 字");
            }
            List<Long> ownerIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .like(User::getUsername, value)
                            .select(User::getId))
                    .stream().map(User::getId).toList();
            query.and(w -> {
                w.like(Merchant::getName, value);
                if (!ownerIds.isEmpty()) {
                    w.or().in(Merchant::getUserId, ownerIds);
                }
            });
        }
        IPage<Merchant> result = merchantMapper.selectPage(new Page<>(page, size), query);
        List<MerchantAdminView> views = result.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, result.getTotal(), page, size);
    }

    public MerchantAdminView get(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商家不存在");
        }
        return toView(merchant);
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantAdminView updateStatus(Long id, MerchantAuditRequest request) {
        boolean hasAudit = request.getAuditStatus() != null && !request.getAuditStatus().isBlank();
        boolean hasStatus = request.getStatus() != null && !request.getStatus().isBlank();
        if (!hasAudit && !hasStatus) {
            throw new BizException(ErrorCode.PARAM_INVALID, "auditStatus 与 status 至少指定一项");
        }
        if (hasAudit && "REJECTED".equals(request.getAuditStatus())
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核拒绝时必须填写原因");
        }
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商家不存在");
        }
        boolean auditChanged = hasAudit && !request.getAuditStatus().equals(merchant.getAuditStatus());
        boolean statusChanged = hasStatus && !request.getStatus().equals(merchant.getStatus());
        if (!auditChanged && !statusChanged) {
            return toView(merchant);
        }
        String detail = request.getReason() == null || request.getReason().isBlank()
                ? "审核/状态变更" : request.getReason().trim();
        if (hasAudit) {
            merchant.setAuditStatus(request.getAuditStatus());
        }
        if (hasStatus) {
            merchant.setStatus(request.getStatus());
        }
        int rows = merchantMapper.updateById(merchant);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "商家信息已变更，请刷新后重试");
        }
        if (auditChanged) {
            auditService.event(roleGuard.currentUser(), AuditActions.MERCHANT_AUDIT, "MERCHANT",
                    String.valueOf(id), detail, true);
        }
        if (statusChanged) {
            auditService.event(roleGuard.currentUser(), AuditActions.MERCHANT_STATUS_CHANGE, "MERCHANT",
                    String.valueOf(id), detail, true);
        }
        log.info("商家审核/状态已更新 merchantId={}, auditStatus={}, status={}", id,
                merchant.getAuditStatus(), merchant.getStatus());
        return toView(merchantMapper.selectById(id));
    }

    private MerchantAdminView toView(Merchant merchant) {
        User owner = userMapper.selectById(merchant.getUserId());
        long orderCount = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getMerchantId, merchant.getId()));
        return new MerchantAdminView(merchant.getId(), merchant.getUserId(),
                owner == null ? null : owner.getUsername(), merchant.getName(), merchant.getPhone(),
                merchant.getAddress(), merchant.getLogoUrl(), scale(merchant.getDeliveryFee()),
                scale(merchant.getMinOrderAmount()), merchant.getBusinessStatus(), merchant.getAuditStatus(),
                merchant.getStatus(), orderCount, merchant.getCreatedAt());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validateAuditStatus(String auditStatus) {
        if (auditStatus != null && !auditStatus.isBlank()
                && !"APPROVED".equals(auditStatus) && !"REJECTED".equals(auditStatus)
                && !"PENDING".equals(auditStatus)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的审核状态");
        }
    }

    private static void validateMerchantStatus(String status) {
        if (status != null && !status.isBlank() && !"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的商家状态");
        }
    }
}
