package com.restaurant.contract;

import java.math.BigDecimal;

/** 跨域端口：读取商家下单必需信息（实现方：merchant 域）。 */
public interface MerchantPort {

    /**
     * 读取商家快照。
     *
     * @param merchantId 商家 ID
     * @return 不存在返回 {@code null}
     */
    MerchantSnapshot get(Long merchantId);

    /**
     * 商家下单快照。
     *
     * @param id             商家 ID
     * @param userId         商家账号 ID
     * @param name           店铺名
     * @param businessStatus OPEN / CLOSED
     * @param auditStatus    PENDING / APPROVED / REJECTED
     * @param status         ENABLED / DISABLED
     * @param deliveryFee    配送费
     * @param minOrderAmount 起送价
     */
    record MerchantSnapshot(Long id,
                            Long userId,
                            String name,
                            String businessStatus,
                            String auditStatus,
                            String status,
                            BigDecimal deliveryFee,
                            BigDecimal minOrderAmount) {
    }
}
