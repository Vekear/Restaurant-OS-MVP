package com.restaurant.table.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.table.TableSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * EXPIRED → CLOSED：补救旧版本缺陷遗留的“已过期但仍占用桌台”会话（closed_at 已由 expire 写入）。
     */
    @Update("UPDATE table_session SET status='CLOSED', version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND status='EXPIRED'")
    int closeExpired(@Param("id") Long id);

    /**
     * 补救扫描：EXPIRED 会话仍被桌台引用且桌台仍为 OCCUPIED 的会话 ID（旧缺陷遗留数据）。
     */
    @Select("SELECT s.id FROM table_session s "
            + "JOIN table_info t ON t.current_session_id = s.id "
            + "WHERE s.status='EXPIRED' AND t.status='OCCUPIED' AND t.deleted=0 "
            + "ORDER BY s.id LIMIT #{limit}")
    List<Long> selectExpiredOccupiedSessionIds(@Param("limit") int limit);

    /** 记录当前进行中订单。 */
    @Update("UPDATE table_session SET current_order_id=#{orderId}, last_order_at=CURRENT_TIMESTAMP(3), "
            + "version=version+1, updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='ACTIVE'")
    int touchOrder(@Param("id") Long id, @Param("orderId") Long orderId);

    /** 续期（复用会话时延长过期时间）。 */
    @Update("UPDATE table_session SET expire_at=#{expireAt}, version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND status='ACTIVE'")
    int renew(@Param("id") Long id, @Param("expireAt") LocalDateTime expireAt);
}
