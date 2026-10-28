package com.restaurant.merchant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.audit.AuditActions;
import com.restaurant.audit.AuditService;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
import com.restaurant.merchant.Merchant;
import com.restaurant.merchant.MerchantGuard;
import com.restaurant.merchant.dto.BusinessStatusRequest;
import com.restaurant.merchant.dto.MerchantProfileUpdateRequest;
import com.restaurant.merchant.mapper.MerchantMapper;
import com.restaurant.merchant.vo.MerchantAdminView;
import com.restaurant.merchant.vo.MerchantProfileView;
import com.restaurant.order.Order;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.security.RoleGuard;
import com.restaurant.security.UserPrincipal;
import com.restaurant.user.User;
import com.restaurant.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 商家店铺资料与营业状态（{@code docs/02-api.md §15.1–15.4}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantProfileService {

    private final MerchantMapper merchantMapper;

    private final UserMapper userMapper;

    private final OrderMapper orderMapper;

    private final MerchantGuard merchantGuard;

    private final RoleGuard roleGuard;

    private final AuditService auditService;

    /** 当前商家资料。 */
    public MerchantProfileView profile() {
        Merchant merchant = merchantGuard.requireActiveMerchant();
        User owner = userMapper.selectById(merchant.getUserId());
        return new MerchantProfileView(merchant.getId(), merchant.getUserId(),
                owner == null ? null : owner.getUsername(), merchant.getName(), merchant.getDescription(),
                merchant.getLogoUrl(), merchant.getPhone(), merchant.getAddress(), scale(merchant.getDeliveryFee()),
                scale(merchant.getMinOrderAmount()), merchant.getBusinessStatus(), merchant.getAuditStatus(),
                merchant.getStatus(), merchant.getCreatedAt());
    }

    /** 更新店铺资料（乐观锁），返回管理端视图（含订单数）。 */
    @Transactional(rollbackFor = Exception.class)
    public MerchantAdminView update(MerchantProfileUpdateRequest request) {
        Merchant merchant = merchantGuard.requireActiveMerchant();
        merchant.setName(request.getName().trim());
        merchant.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        merchant.setPhone(request.getPhone().trim());
        merchant.setAddress(request.getAddress().trim());
        merchant.setLogoUrl(request.getLogoUrl() == null || request.getLogoUrl().isBlank()
                ? null : request.getLogoUrl().trim());
        merchant.setDeliveryFee(scale(request.getDeliveryFee()));
        merchant.setMinOrderAmount(scale(request.getMinOrderAmount()));
        int rows = merchantMapper.updateById(merchant);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "商家信息已变更，请刷新后重试");
        }
        auditService.event(roleGuard.currentUser(), AuditActions.MERCHANT_PROFILE_UPDATE, "MERCHANT",
                String.valueOf(merchant.getId()), "更新店铺资料", true);
        log.info("商家资料已更新 merchantId={}", merchant.getId());
        return toAdminView(merchantMapper.selectById(merchant.getId()));
    }

    /** 当前营业状态。 */
    public String businessStatus() {
        return merchantGuard.requireActiveMerchant().getBusinessStatus();
    }

    /** 切换营业状态（仅影响新订单拦截）。 */
    @Transactional(rollbackFor = Exception.class)
    public String updateBusinessStatus(BusinessStatusRequest request) {
        Merchant merchant = merchantGuard.requireActiveMerchant();
        String target = request.getBusinessStatus();
        if (!target.equals(merchant.getBusinessStatus())) {
            merchant.setBusinessStatus(target);
            int rows = merchantMapper.updateById(merchant);
            if (rows == 0) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "商家信息已变更，请刷新后重试");
            }
            UserPrincipal principal = roleGuard.currentUser();
            auditService.event(principal, AuditActions.BUSINESS_STATUS_CHANGE, "MERCHANT",
                    String.valueOf(merchant.getId()), target, true);
            log.info("营业状态已切换 merchantId={}, status={}", merchant.getId(), target);
        }
        return target;
    }

    /** 管理端视图（内部复用）。 */
    MerchantAdminView toAdminView(Merchant merchant) {
        if (merchant == null) {
            return null;
        }
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
}
