package com.restaurant.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.order.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 订单 Mapper：状态机使用「乐观锁 + 前置状态」原子更新。 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 状态机原子流转：仅当 {@code id + version + status} 全部匹配才更新。
     *
     * @return 影响行数（0 表示并发冲突或前置状态不符，调用方抛 40905）
     */
    @Update("""
            <script>
            UPDATE orders
            SET status = #{targetStatus},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
                <if test="paidAt != null">, paid_at = #{paidAt}</if>
                <if test="finishedAt != null">, finished_at = #{finishedAt}</if>
                <if test="cancelReason != null">, cancel_reason = #{cancelReason}</if>
            WHERE id = #{id} AND version = #{version} AND status = #{currentStatus}
            </script>
            """)
    int transit(@Param("id") Long id,
                @Param("currentStatus") String currentStatus,
                @Param("version") Integer version,
                @Param("targetStatus") String targetStatus,
                @Param("paidAt") LocalDateTime paidAt,
                @Param("finishedAt") LocalDateTime finishedAt,
                @Param("cancelReason") String cancelReason);
}
