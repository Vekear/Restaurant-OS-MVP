package com.restaurant.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.payment.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 支付单 Mapper：退款/关闭/成功均为条件更新，避免并发重复处理。 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    /**
     * 支付成功状态守卫：仅 PENDING → SUCCESS 首次生效。
     *
     * <p>并发回调只有一个线程能更新 1 行，其余线程更新 0 行；调用方据此避免
     * 重复发布 {@code PaymentSucceededEvent}（进而重复推进订单状态）。
     */
    @Update("""
            UPDATE payment
            SET status = 'SUCCESS', transaction_no = #{transactionNo}, paid_at = #{paidAt},
                callback_at = CURRENT_TIMESTAMP(3), version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markSuccessIfPending(@Param("id") Long id,
                             @Param("transactionNo") String transactionNo,
                             @Param("paidAt") LocalDateTime paidAt);

    /**
     * 当前读支付单状态：用于条件更新 0 行后判断并发事务是否已完成 SUCCESS。
     *
     * <p>REPEATABLE READ 下普通 SELECT 会命中旧快照，必须 FOR UPDATE 走当前读。
     */
    @Select("SELECT status FROM payment WHERE id = #{id} FOR UPDATE")
    String selectStatusForUpdate(@Param("id") Long id);

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
