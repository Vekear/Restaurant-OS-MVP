package com.restaurant.table.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.table.TableSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 桌台会话 Mapper：关闭/过期/绑定订单/续期均以 ACTIVE 为前提。 */
@Mapper
public interface TableSessionMapper extends BaseMapper<TableSession> {

    /** ACTIVE → CLOSED（乐观锁）。 */
    @Update("UPDATE table_session SET status='CLOSED', closed_at=CURRENT_TIMESTAMP(3), "
            + "version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND status='ACTIVE' AND version=#{version}")
    int close(@Param("id") Long id, @Param("version") Integer version);

    /** ACTIVE → EXPIRED（清理任务使用）。 */
    @Update("UPDATE table_session SET status='EXPIRED', closed_at=CURRENT_TIMESTAMP(3), "
            + "version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND status='ACTIVE'")
    int expire(@Param("id") Long id);

    /** 记录当前进行中订单。 */
    @Update("UPDATE table_session SET current_order_id=#{orderId}, last_order_at=CURRENT_TIMESTAMP(3), "
            + "version=version+1, updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='ACTIVE'")
    int touchOrder(@Param("id") Long id, @Param("orderId") Long orderId);

    /** 续期（复用会话时延长过期时间）。 */
    @Update("UPDATE table_session SET expire_at=#{expireAt}, version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND status='ACTIVE'")
    int renew(@Param("id") Long id, @Param("expireAt") LocalDateTime expireAt);
}
