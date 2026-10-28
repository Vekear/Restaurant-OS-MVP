package com.restaurant.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.admin.dto.CouponCreateRequest;
import com.restaurant.admin.dto.CouponStatusRequest;
import com.restaurant.admin.dto.CouponUpdateRequest;
import com.restaurant.admin.vo.CouponView;
import com.restaurant.audit.AuditActions;
import com.restaurant.audit.AuditService;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.exception.BizException;
import com.restaurant.coupon.Coupon;
import com.restaurant.coupon.mapper.CouponMapper;
import com.restaurant.security.RoleGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** 管理后台优惠券（{@code docs/02-api.md §20.9–20.14}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCouponService {

    private final CouponMapper couponMapper;

    private final RoleGuard roleGuard;

    private final AuditService auditService;

    public PageResult<CouponView> list(String status, String keyword, int page, int size) {
        if (status != null && !status.isBlank()
                && !"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的优惠券状态");
        }
        var query = Wrappers.<Coupon>lambdaQuery()
                .eq(status != null && !status.isBlank(), Coupon::getStatus, status)
                .orderByDesc(Coupon::getCreatedAt)
                .orderByDesc(Coupon::getId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            if (value.length() > 30) {
                throw new BizException(ErrorCode.PARAM_INVALID, "keyword 不能超过 30 字");
            }
            query.like(Coupon::getName, value);
        }
        IPage<Coupon> result = couponMapper.selectPage(new Page<>(page, size), query);
        List<CouponView> views = result.getRecords().stream().map(AdminCouponService::toView).toList();
        return PageResult.of(views, result.getTotal(), page, size);
    }

    public CouponView get(Long id) {
        return toView(requireCoupon(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public CouponView create(CouponCreateRequest request) {
        validateAmounts(request.getThresholdAmount(), request.getDiscountAmount());
        validateTime(request);
        Coupon coupon = new Coupon();
        coupon.setName(request.getName().trim());
        coupon.setType("FULL_REDUCTION");
        coupon.setThresholdAmount(scale(request.getThresholdAmount()));
        coupon.setDiscountAmount(scale(request.getDiscountAmount()));
        coupon.setTotal(request.getTotal());
        coupon.setRemaining(request.getTotal());
        coupon.setStartAt(request.getStartAt());
        coupon.setEndAt(request.getEndAt());
        coupon.setStatus("ENABLED");
        coupon.setVersion(0);
        couponMapper.insert(coupon);
        auditService.event(roleGuard.currentUser(), AuditActions.COUPON_CREATE, "COUPON",
                String.valueOf(coupon.getId()), coupon.getName(), true);
        log.info("优惠券已创建 couponId={}, name={}", coupon.getId(), coupon.getName());
        return toView(coupon);
    }

    @Transactional(rollbackFor = Exception.class)
    public CouponView update(Long id, CouponUpdateRequest request) {
        validateAmounts(request.getThresholdAmount(), request.getDiscountAmount());
        validateTime(request);
        Coupon coupon = requireCoupon(id);
        int claimed = coupon.getTotal() - coupon.getRemaining();
        if (request.getTotal() < claimed) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "发行量不能小于已领取数量 " + claimed);
        }
        coupon.setName(request.getName().trim());
        coupon.setThresholdAmount(scale(request.getThresholdAmount()));
        coupon.setDiscountAmount(scale(request.getDiscountAmount()));
        coupon.setTotal(request.getTotal());
        coupon.setRemaining(request.getTotal() - claimed);
        coupon.setStartAt(request.getStartAt());
        coupon.setEndAt(request.getEndAt());
        if (couponMapper.updateById(coupon) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "优惠券已变更，请刷新后重试");
        }
        auditService.event(roleGuard.currentUser(), AuditActions.COUPON_UPDATE, "COUPON",
                String.valueOf(id), coupon.getName(), true);
        log.info("优惠券已更新 couponId={}", id);
        return toView(couponMapper.selectById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Boolean> delete(Long id) {
        Coupon coupon = requireCoupon(id);
        if (coupon.getRemaining() != coupon.getTotal()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "已有用户领取，请改为停用");
        }
        couponMapper.deleteById(id);
        auditService.event(roleGuard.currentUser(), AuditActions.COUPON_DELETE, "COUPON",
                String.valueOf(id), coupon.getName(), true);
        log.info("优惠券已删除 couponId={}", id);
        return Map.of("deleted", true);
    }

    @Transactional(rollbackFor = Exception.class)
    public CouponView updateStatus(Long id, CouponStatusRequest request) {
        Coupon coupon = requireCoupon(id);
        if (!request.getStatus().equals(coupon.getStatus())) {
            coupon.setStatus(request.getStatus());
            if (couponMapper.updateById(coupon) == 0) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "优惠券已变更，请刷新后重试");
            }
            auditService.event(roleGuard.currentUser(), AuditActions.COUPON_STATUS_CHANGE, "COUPON",
                    String.valueOf(id), request.getStatus(), true);
            log.info("优惠券状态已切换 couponId={}, status={}", id, request.getStatus());
        }
        return toView(coupon);
    }

    private Coupon requireCoupon(Long id) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        return coupon;
    }

    private static void validateAmounts(BigDecimal threshold, BigDecimal discount) {
        if (discount.compareTo(threshold) >= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "优惠金额必须小于使用门槛");
        }
    }

    private static void validateTime(CouponCreateRequest request) {
        if (!request.getStartAt().isBefore(request.getEndAt())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始时间必须早于结束时间");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static CouponView toView(Coupon coupon) {
        return new CouponView(coupon.getId(), coupon.getName(), coupon.getType(),
                scale(coupon.getThresholdAmount()), scale(coupon.getDiscountAmount()),
                coupon.getTotal(), coupon.getRemaining(), coupon.getStartAt(), coupon.getEndAt(),
                coupon.getStatus(), coupon.getCreatedAt());
    }
}
