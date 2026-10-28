package com.restaurant.stat.mapper;

import com.restaurant.stat.row.HotDishRow;
import com.restaurant.stat.row.MerchantRankRow;
import com.restaurant.stat.row.RatioRow;
import com.restaurant.stat.row.TrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计只读查询（商家看板 + 管理后台共用）。
 *
 * <p>口径：Asia/Shanghai 自然日，仅 {@code status=COMPLETED} 计入营业额，金额取 {@code orders.pay_amount}。
 */
@Mapper
public interface StatMapper {

    /** 区间内订单总数（全部状态）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM orders o
            WHERE 1 = 1
            <if test="merchantId != null"> AND o.merchant_id = #{merchantId}</if>
            <if test="start != null"> AND o.created_at &gt;= #{start}</if>
            <if test="end != null"> AND o.created_at &lt;= #{end}</if>
            </script>
            """)
    long countAll(@Param("merchantId") Long merchantId,
                  @Param("start") LocalDateTime start,
                  @Param("end") LocalDateTime end);

    /** 区间内指定状态订单数。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM orders o
            WHERE o.status = #{status}
            <if test="merchantId != null"> AND o.merchant_id = #{merchantId}</if>
            <if test="start != null"> AND o.created_at &gt;= #{start}</if>
            <if test="end != null"> AND o.created_at &lt;= #{end}</if>
            </script>
            """)
    long countByStatus(@Param("merchantId") Long merchantId,
                       @Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end,
                       @Param("status") String status);

    /** 已完成订单实付金额合计。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(o.pay_amount), 0) FROM orders o
            WHERE o.status = 'COMPLETED'
            <if test="merchantId != null"> AND o.merchant_id = #{merchantId}</if>
            <if test="start != null"> AND o.created_at &gt;= #{start}</if>
            <if test="end != null"> AND o.created_at &lt;= #{end}</if>
            </script>
            """)
    BigDecimal sumCompletedPayAmount(@Param("merchantId") Long merchantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /** 已退款订单的实付金额合计（存在 REFUNDED 支付单）。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(o.pay_amount), 0) FROM orders o
            WHERE EXISTS (SELECT 1 FROM payment p WHERE p.order_id = o.id AND p.status = 'REFUNDED')
            <if test="merchantId != null"> AND o.merchant_id = #{merchantId}</if>
            <if test="start != null"> AND o.created_at &gt;= #{start}</if>
            <if test="end != null"> AND o.created_at &lt;= #{end}</if>
            </script>
            """)
    BigDecimal sumRefundPayAmount(@Param("merchantId") Long merchantId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /** 低库存菜品数（在售、未删除、库存 <= 阈值）。 */
    @Select("""
            SELECT COUNT(*) FROM dish d
            WHERE d.deleted = 0 AND d.status = 'ON_SALE' AND d.stock <= #{threshold}
              AND d.merchant_id = #{merchantId}
            """)
    long countLowStock(@Param("merchantId") Long merchantId, @Param("threshold") int threshold);

    /** 按自然日趋势（已完成订单）。 */
    @Select("""
            <script>
            SELECT DATE_FORMAT(o.created_at, '%Y-%m-%d') AS date,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(o.pay_amount), 0) AS revenue
            FROM orders o
            WHERE o.status = 'COMPLETED'
            <if test="merchantId != null"> AND o.merchant_id = #{merchantId}</if>
            <if test="start != null"> AND o.created_at &gt;= #{start}</if>
            <if test="end != null"> AND o.created_at &lt;= #{end}</if>
            GROUP BY DATE_FORMAT(o.created_at, '%Y-%m-%d')
            ORDER BY date ASC
            </script>
            """)
    List<TrendRow> selectTrend(@Param("merchantId") Long merchantId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /** 热销菜品（按菜品 ID 聚合，商家看板用）。 */
    @Select("""
            <script>
            SELECT oi.dish_id AS dishId, oi.dish_name AS dishName,
                   SUM(oi.quantity) AS quantity, COALESCE(SUM(oi.subtotal), 0) AS amount
            FROM order_item oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.status = 'COMPLETED' AND oi.dish_id IS NOT NULL
            <if test="merchantId != null"> AND o.merchant_id = #{merchantId}</if>
            <if test="start != null"> AND o.created_at &gt;= #{start}</if>
            <if test="end != null"> AND o.created_at &lt;= #{end}</if>
            GROUP BY oi.dish_id, oi.dish_name
            ORDER BY quantity DESC, amount DESC
            LIMIT #{limit}
            </script>
            """)
    List<HotDishRow> selectHotDishes(@Param("merchantId") Long merchantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     @Param("limit") int limit);

    /** 热销菜品（跨店按菜品名合并，管理后台用）。 */
    @Select("""
            <script>
            SELECT MIN(oi.dish_id) AS dishId, oi.dish_name AS dishName,
                   SUM(oi.quantity) AS quantity, COALESCE(SUM(oi.subtotal), 0) AS amount
            FROM order_item oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.status = 'COMPLETED'
            <if test="start != null"> AND o.created_at &gt;= #{start}</if>
            <if test="end != null"> AND o.created_at &lt;= #{end}</if>
            GROUP BY oi.dish_name
            ORDER BY quantity DESC, amount DESC
            LIMIT #{limit}
            </script>
            """)
    List<HotDishRow> selectHotDishesByName(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           @Param("limit") int limit);

    /** 订单类型占比（已完成订单）。 */
    @Select("""
            <script>
            SELECT o.order_type AS orderType, COUNT(*) AS orderCount,
                   COALESCE(SUM(o.pay_amount), 0) AS orderAmount
            FROM orders o
            WHERE o.status = 'COMPLETED'
            <if test="merchantId != null"> AND o.merchant_id = #{merchantId}</if>
            <if test="start != null"> AND o.created_at &gt;= #{start}</if>
            <if test="end != null"> AND o.created_at &lt;= #{end}</if>
            GROUP BY o.order_type
            </script>
            """)
    List<RatioRow> selectTypeRatio(@Param("merchantId") Long merchantId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /** 用户总数。 */
    @Select("SELECT COUNT(*) FROM users")
    long countUsers();

    /** 区间新增用户数。 */
    @Select("""
            SELECT COUNT(*) FROM users u
            WHERE u.created_at >= #{start} AND u.created_at <= #{end}
            """)
    long countNewUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 已审核通过的商家数。 */
    @Select("SELECT COUNT(*) FROM merchant m WHERE m.audit_status = 'APPROVED'")
    long countApprovedMerchants();

    /** 商家营业额排行（已完成订单，导出用）。 */
    @Select("""
            SELECT o.merchant_id AS merchantId, m.name AS merchantName,
                   COUNT(*) AS orderCount, COALESCE(SUM(o.pay_amount), 0) AS revenue
            FROM orders o
            JOIN merchant m ON m.id = o.merchant_id
            WHERE o.status = 'COMPLETED'
              AND o.created_at >= #{start} AND o.created_at <= #{end}
            GROUP BY o.merchant_id, m.name
            ORDER BY revenue DESC, orderCount DESC
            LIMIT #{limit}
            """)
    List<MerchantRankRow> selectMerchantRanking(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("limit") int limit);
}
