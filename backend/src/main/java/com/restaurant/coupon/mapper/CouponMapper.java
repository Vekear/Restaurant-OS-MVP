package com.restaurant.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.coupon.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 优惠券模板 Mapper。 */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 原子扣减剩余量：仅当仍有库存且券处于启用状态时成功（返回 1）。
     * 并发领券由该 UPDATE 保证不超发。
     */
    @Update("""
            UPDATE coupon
            SET remaining = remaining - 1, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{couponId} AND remaining > 0 AND status = 'ENABLED'
            """)
    int decrementRemaining(@Param("couponId") Long couponId);
}
