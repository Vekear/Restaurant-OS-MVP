package com.restaurant.merchant;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
import com.restaurant.merchant.mapper.MerchantMapper;
import com.restaurant.security.RoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 商家端统一守卫：角色 MERCHANT 且店铺审核通过、启用中；否则 40301/40303。
 */
@Component
@RequiredArgsConstructor
public class MerchantGuard {

    private final MerchantMapper merchantMapper;

    private final RoleGuard roleGuard;

    /** 当前登录商家的店铺（已校验 APPROVED + ENABLED）。 */
    public Merchant requireActiveMerchant() {
        Long merchantId = roleGuard.requireMerchantId();
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || !"APPROVED".equals(merchant.getAuditStatus())
                || !"ENABLED".equals(merchant.getStatus())) {
            throw new BizException(ErrorCode.MERCHANT_UNAVAILABLE, "商家不存在或不可用");
        }
        return merchant;
    }

    /** 当前登录商家的店铺 ID（已校验可用）。 */
    public Long requireActiveMerchantId() {
        return requireActiveMerchant().getId();
    }
}
