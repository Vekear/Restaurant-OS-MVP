package com.restaurant.merchant.adapter;

import com.restaurant.contract.MerchantPort;
import com.restaurant.merchant.Merchant;
import com.restaurant.merchant.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link MerchantPort} 的商家域实现。 */
@Component
@RequiredArgsConstructor
public class MerchantPortAdapter implements MerchantPort {

    private final MerchantMapper merchantMapper;

    @Override
    public MerchantSnapshot get(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return null;
        }
        return new MerchantSnapshot(merchant.getId(), merchant.getUserId(), merchant.getName(),
                merchant.getBusinessStatus(), merchant.getAuditStatus(), merchant.getStatus(),
                merchant.getDeliveryFee(), merchant.getMinOrderAmount());
    }
}
