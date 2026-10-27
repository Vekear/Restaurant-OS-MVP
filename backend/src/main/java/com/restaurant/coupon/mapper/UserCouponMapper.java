package com.restaurant.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.coupon.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 用户优惠券 Mapper：占用/回补均为条件更新。 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /** 原子核销：UNUSED → USED，0 行表示不可用。 */
    @Update("""
            UPDATE user_coupon
            SET status = 'USED', order_id = #{orderId}, used_at = CURRENT_TIMESTAMP(3),
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{userCouponId} AND user_id = #{userId} AND status = 'UNUSED'
            """)
    int consume(@Param("userCouponId") Long userCouponId,
                @Param("userId") Long userId,
                @Param("orderId") Long orderId);

    /** 回补：USED → UNUSED。 */
    @Update("""
            UPDATE user_coupon
            SET status = 'UNUSED', order_id = NULL, used_at = NULL,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{userCouponId} AND status = 'USED'
            """)
    int restore(@Param("userCouponId") Long userCouponId);
}
