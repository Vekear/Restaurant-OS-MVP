package com.restaurant.contract;

import java.math.BigDecimal;

/** 跨域端口：优惠券试算/占用/回补（实现方：coupon 域）。 */
public interface CouponPort {

    /**
     * 计算优惠金额并确认可用（不可叠加，最多 1 张）。
     *
     * @param userId       用户 ID
     * @param userCouponId 用户优惠券 ID
     * @param totalAmount  商品总金额
     * @return 优惠结果（discountAmount 为 0 表示未使用）
     */
    CouponUsage quote(Long userId, Long userCouponId, BigDecimal totalAmount);

    /** 原子核销用户券（UNUSED → USED），失败抛 40912。 */
    void consume(Long userCouponId, Long orderId);

    /** 回补用户券（USED → UNUSED），订单取消/拒单时调用。 */
    void restore(Long userCouponId);

    /**
     * 优惠计算结果。
     *
     * @param userCouponId   用户优惠券 ID（可为 null）
     * @param couponId       券模板 ID（可为 null）
     * @param discountAmount 优惠金额
     */
    record CouponUsage(Long userCouponId, Long couponId, BigDecimal discountAmount) {
    }
}
