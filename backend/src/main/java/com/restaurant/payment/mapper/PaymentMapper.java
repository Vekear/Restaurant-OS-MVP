package com.restaurant.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.payment.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 支付单 Mapper：退款/关闭为条件更新，避免并发重复处理。 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    /** 已支付订单取消/拒单：SUCCESS → REFUNDED。 */
    @Update("""
            UPDATE payment
            SET status = 'REFUNDED', version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE order_id = #{orderId} AND status = 'SUCCESS'
            """)
    int refundByOrder(@Param("orderId") Long orderId);

    /** 订单超时/取消：未支付单关闭（PENDING → FAILED）。 */
    @Update("""
            UPDATE payment
            SET status = 'FAILED', version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE order_id = #{orderId} AND status = 'PENDING'
            """)
    int closePendingByOrder(@Param("orderId") Long orderId);
}
