package com.restaurant.coupon;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.CouponPort;
import com.restaurant.coupon.mapper.CouponMapper;
import com.restaurant.coupon.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** {@link CouponPort} 的优惠券域实现。 */
@Component
@RequiredArgsConstructor
public class CouponPortAdapter implements CouponPort {

    private final CouponMapper couponMapper;

    private final UserCouponMapper userCouponMapper;

    @Override
    public CouponUsage quote(Long userId, Long userCouponId, BigDecimal totalAmount) {
        if (userCouponId == null) {
            return new CouponUsage(null, null, BigDecimal.ZERO);
        }
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !userId.equals(userCoupon.getUserId()) || !"UNUSED".equals(userCoupon.getStatus())) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "优惠券不可用");
        }
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        LocalDateTime now = LocalDateTime.now();
        if (coupon == null || !"ENABLED".equals(coupon.getStatus())
                || now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "优惠券已过期或已停用");
        }
        if (coupon.getThresholdAmount() != null && totalAmount.compareTo(coupon.getThresholdAmount()) < 0) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "未达优惠券使用门槛");
        }
        BigDecimal discount = coupon.getDiscountAmount() == null ? BigDecimal.ZERO : coupon.getDiscountAmount();
        if (discount.compareTo(totalAmount) > 0) {
            discount = totalAmount;
        }
        return new CouponUsage(userCoupon.getId(), coupon.getId(), discount);
    }

    @Override
    public void consume(Long userCouponId, Long orderId) {
        if (userCouponId == null) {
            return;
        }
        int rows = userCouponMapper.consume(userCouponId, null, orderId);
        if (rows == 0) {
            // userId 已在 quote 阶段校验；此处 0 行只可能是状态被并发改变
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "优惠券不可用");
        }
    }

    @Override
    public void restore(Long userCouponId) {
        if (userCouponId == null) {
            return;
        }
        userCouponMapper.restore(userCouponId);
    }
}
