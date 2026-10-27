package com.restaurant.coupon;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 用户领取的优惠券 {@code user_coupon}（unique(user_id, coupon_id)）。 */
@Getter
@Setter
@TableName("user_coupon")
public class UserCoupon extends BaseEntity {

    private Long userId;

    private Long couponId;

    /** UNUSED / USED / EXPIRED。 */
    private String status;

    private Long orderId;

    private LocalDateTime receivedAt;

    private LocalDateTime usedAt;

    @Version
    private Integer version;
}
