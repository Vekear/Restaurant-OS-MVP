package com.restaurant.coupon.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.enums.Role;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.MerchantPort;
import com.restaurant.coupon.Coupon;
import com.restaurant.coupon.UserCoupon;
import com.restaurant.coupon.dto.CouponQuoteRequest;
import com.restaurant.coupon.mapper.CouponMapper;
import com.restaurant.coupon.mapper.UserCouponMapper;
import com.restaurant.coupon.vo.CouponView;
import com.restaurant.coupon.vo.QuoteView;
import com.restaurant.coupon.vo.UserCouponView;
import com.restaurant.security.RoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 顾客侧优惠券服务（docs/02-api.md §8）。
 *
 * <p>满减券全场通用、不可叠加、单用户每券限领 1 张；领取通过
 * {@code UPDATE coupon SET remaining = remaining - 1 WHERE remaining > 0} 原子扣减，防超发。
 */
@Service
@RequiredArgsConstructor
public class CustomerCouponService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String USER_STATUS_UNUSED = "UNUSED";
    private static final Set<String> MINE_STATUSES = Set.of("UNUSED", "USED", "EXPIRED", "ALL");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final MerchantPort merchantPort;
    private final RoleGuard roleGuard;

    @Transactional(readOnly = true)
    public PageResult<CouponView> list(BigDecimal amount, int page, int size) {
        Long userId = requireCustomer();
        LocalDateTime now = LocalDateTime.now();
        Page<Coupon> couponPage = couponMapper.selectPage(new Page<>(page, size),
                Wrappers.<Coupon>lambdaQuery()
                        .eq(Coupon::getStatus, STATUS_ENABLED)
                        .le(Coupon::getStartAt, now)
                        .ge(Coupon::getEndAt, now)
                        .gt(Coupon::getRemaining, 0)
                        .orderByAsc(Coupon::getId));
        Set<Long> claimedIds = claimedCouponIds(userId, couponPage.getRecords().stream()
                .map(Coupon::getId).toList());
        List<CouponView> views = couponPage.getRecords().stream()
                .map(coupon -> toCouponView(coupon, claimedIds.contains(coupon.getId()), amount))
                .toList();
        return PageResult.of(views, couponPage.getTotal(), page, size);
    }

    @Transactional
    public UserCouponView claim(Long couponId) {
        Long userId = requireCustomer();
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        if (!availableNow(coupon, LocalDateTime.now())) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "活动未开始或已结束");
        }
        Long owned = userCouponMapper.selectCount(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        if (owned != null && owned > 0) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "您已领取过该券");
        }
        int remaining = couponMapper.decrementRemaining(couponId);
        if (remaining == 0) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "已领完");
        }
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(USER_STATUS_UNUSED);
        userCoupon.setReceivedAt(LocalDateTime.now());
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "您已领取过该券");
        }
        return toUserCouponView(userCoupon, coupon, null);
    }

    @Transactional(readOnly = true)
    public PageResult<UserCouponView> mine(String status, BigDecimal amount, int page, int size) {
        Long userId = requireCustomer();
        String normalized = status == null || status.isBlank() ? USER_STATUS_UNUSED : status.toUpperCase();
        if (!MINE_STATUSES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的优惠券状态");
        }
        List<UserCoupon> owned = userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(!"ALL".equals(normalized), UserCoupon::getStatus, normalized));
        Map<Long, Coupon> coupons = loadCoupons(owned.stream().map(UserCoupon::getCouponId).toList());
        List<UserCoupon> filtered = owned.stream()
                .filter(item -> coupons.containsKey(item.getCouponId()))
                .sorted(mineComparator(normalized, coupons))
                .toList();
        int total = filtered.size();
        int from = Math.min(Math.max(page - 1, 0) * size, total);
        int to = Math.min(from + size, total);
        List<UserCouponView> views = filtered.subList(from, to).stream()
                .map(item -> toUserCouponView(item, coupons.get(item.getCouponId()), amount))
                .toList();
        return PageResult.of(views, total, page, size);
    }

    @Transactional(readOnly = true)
    public QuoteView quote(CouponQuoteRequest request) {
        Long userId = requireCustomer();
        MerchantPort.MerchantSnapshot merchant = merchantPort.get(request.getMerchantId());
        if (merchant == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商家不存在");
        }
        if (!"APPROVED".equals(merchant.auditStatus()) || !"ENABLED".equals(merchant.status())) {
            throw new BizException(ErrorCode.MERCHANT_UNAVAILABLE, "商家不存在或不可用");
        }
        BigDecimal totalAmount = scale(request.getTotalAmount());
        BigDecimal deliveryFee = merchant.deliveryFee() == null ? ZERO : scale(merchant.deliveryFee());
        BigDecimal minOrderAmount = merchant.minOrderAmount() == null ? ZERO : scale(merchant.minOrderAmount());
        boolean reachMinOrder = totalAmount.compareTo(minOrderAmount) >= 0;

        BigDecimal discountAmount = ZERO;
        UserCouponView selected = null;
        if (request.getUserCouponId() != null) {
            UserCoupon userCoupon = userCouponMapper.selectById(request.getUserCouponId());
            if (userCoupon == null || !userId.equals(userCoupon.getUserId())
                    || !USER_STATUS_UNUSED.equals(userCoupon.getStatus())) {
                throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "优惠券不可用");
            }
            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (coupon == null) {
                throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "优惠券不可用");
            }
            if (!availableNow(coupon, LocalDateTime.now())) {
                throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "优惠券已过期或已停用");
            }
            if (totalAmount.compareTo(threshold(coupon)) < 0) {
                throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "未达优惠券使用门槛");
            }
            discountAmount = coupon.getDiscountAmount() == null ? ZERO : scale(coupon.getDiscountAmount());
            if (discountAmount.compareTo(totalAmount) > 0) {
                discountAmount = totalAmount;
            }
            selected = toUserCouponView(userCoupon, coupon, totalAmount);
        }

        List<UserCoupon> unused = userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, USER_STATUS_UNUSED));
        Map<Long, Coupon> coupons = loadCoupons(unused.stream().map(UserCoupon::getCouponId).toList());
        LocalDateTime now = LocalDateTime.now();
        List<UserCouponView> usableCoupons = unused.stream()
                .filter(item -> {
                    Coupon coupon = coupons.get(item.getCouponId());
                    return coupon != null && availableNow(coupon, now)
                            && totalAmount.compareTo(threshold(coupon)) >= 0;
                })
                .sorted(Comparator
                        .comparing((UserCoupon item) -> {
                            Coupon coupon = coupons.get(item.getCouponId());
                            return coupon.getDiscountAmount() == null ? ZERO : coupon.getDiscountAmount();
                        }, Comparator.reverseOrder())
                        .thenComparing(UserCoupon::getId))
                .map(item -> toUserCouponView(item, coupons.get(item.getCouponId()), totalAmount))
                .toList();

        BigDecimal payAmount = totalAmount.subtract(discountAmount).add(deliveryFee)
                .setScale(2, RoundingMode.HALF_UP);
        return new QuoteView(request.getMerchantId(), totalAmount, deliveryFee, discountAmount,
                payAmount, minOrderAmount, reachMinOrder, selected, usableCoupons);
    }

    private Long requireCustomer() {
        return roleGuard.requireAnyRole(Role.CUSTOMER).getId();
    }

    private Set<Long> claimedCouponIds(Long userId, List<Long> couponIds) {
        if (couponIds.isEmpty()) {
            return Set.of();
        }
        List<UserCoupon> owned = userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .in(UserCoupon::getCouponId, couponIds));
        return owned.stream().map(UserCoupon::getCouponId).collect(Collectors.toCollection(HashSet::new));
    }

    private Map<Long, Coupon> loadCoupons(List<Long> couponIds) {
        List<Long> ids = couponIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return couponMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));
    }

    private Comparator<UserCoupon> mineComparator(String status, Map<Long, Coupon> coupons) {
        if (USER_STATUS_UNUSED.equals(status)) {
            return Comparator.comparing((UserCoupon item) -> {
                        Coupon coupon = coupons.get(item.getCouponId());
                        return coupon == null || coupon.getEndAt() == null ? LocalDateTime.MAX : coupon.getEndAt();
                    })
                    .thenComparing(UserCoupon::getId);
        }
        return Comparator.comparing((UserCoupon item) -> item.getReceivedAt() == null
                        ? LocalDateTime.MIN : item.getReceivedAt())
                .reversed()
                .thenComparing(UserCoupon::getId, Comparator.reverseOrder());
    }

    private boolean availableNow(Coupon coupon, LocalDateTime now) {
        if (!STATUS_ENABLED.equals(coupon.getStatus())) {
            return false;
        }
        if (coupon.getStartAt() != null && now.isBefore(coupon.getStartAt())) {
            return false;
        }
        return coupon.getEndAt() == null || !now.isAfter(coupon.getEndAt());
    }

    private BigDecimal threshold(Coupon coupon) {
        return coupon.getThresholdAmount() == null ? ZERO : coupon.getThresholdAmount();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private CouponView toCouponView(Coupon coupon, boolean claimed, BigDecimal amount) {
        boolean claimable = !claimed && coupon.getRemaining() != null && coupon.getRemaining() > 0;
        Boolean usable = amount == null ? null
                : !claimed && coupon.getRemaining() != null && coupon.getRemaining() > 0
                && amount.compareTo(threshold(coupon)) >= 0;
        return new CouponView(coupon.getId(), coupon.getName(), coupon.getType(),
                coupon.getThresholdAmount(), coupon.getDiscountAmount(),
                coupon.getStartAt(), coupon.getEndAt(), coupon.getTotal(), coupon.getRemaining(),
                coupon.getStatus(), claimed, claimable, usable);
    }

    private UserCouponView toUserCouponView(UserCoupon userCoupon, Coupon coupon, BigDecimal amount) {
        boolean usable;
        String reason = null;
        if (!USER_STATUS_UNUSED.equals(userCoupon.getStatus())) {
            usable = false;
            reason = "USED".equals(userCoupon.getStatus()) ? "已使用" : "已过期";
        } else if (coupon == null || !availableNow(coupon, LocalDateTime.now())) {
            usable = false;
            reason = coupon != null && coupon.getEndAt() != null
                    && LocalDateTime.now().isAfter(coupon.getEndAt()) ? "已过期" : "已锁定";
        } else if (amount != null && amount.compareTo(threshold(coupon)) < 0) {
            usable = false;
            reason = "未达使用门槛";
        } else {
            usable = true;
        }
        return new UserCouponView(userCoupon.getId(), userCoupon.getCouponId(),
                coupon == null ? null : coupon.getName(),
                coupon == null ? null : coupon.getType(),
                coupon == null ? null : coupon.getThresholdAmount(),
                coupon == null ? null : coupon.getDiscountAmount(),
                userCoupon.getStatus(), userCoupon.getReceivedAt(), userCoupon.getUsedAt(),
                coupon == null ? null : coupon.getEndAt(), userCoupon.getOrderId(),
                usable, reason);
    }
}
