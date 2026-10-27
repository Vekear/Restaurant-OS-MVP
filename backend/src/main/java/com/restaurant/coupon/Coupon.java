package com.restaurant.coupon;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 优惠券模板 {@code coupon}（本期仅 FULL_REDUCTION）。 */
@Getter
@Setter
@TableName("coupon")
public class Coupon extends BaseEntity {

    private String name;

    private String type;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Integer total;

    private Integer remaining;

    private String status;

    @Version
    private Integer version;
}
