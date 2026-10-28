package com.restaurant.report.mapper;

import com.restaurant.report.row.OrderExportRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 导出查询（只读）。 */
@Mapper
public interface ReportMapper {

    @Select("""
            <script>
            SELECT o.id AS id,
                   o.order_no AS orderNo,
                   m.name AS merchantName,
                   o.created_at AS createdAt,
                   o.order_type AS orderType,
                   o.status AS status,
                   o.consignee AS consignee,
                   o.consignee_phone AS consigneePhone,
                   o.delivery_address AS deliveryAddress,
                   (SELECT GROUP_CONCAT(CONCAT(oi.dish_name, '×', oi.quantity) ORDER BY oi.id SEPARATOR '；')
                      FROM order_item oi WHERE oi.order_id = o.id) AS dishSummary,
                   o.total_amount AS totalAmount,
                   o.delivery_fee AS deliveryFee,
                   o.discount_amount AS discountAmount,
                   o.pay_amount AS payAmount,
                   p.paid_amount AS mockPaidAmount,
                   p.status AS paymentStatus
            FROM orders o
            JOIN merchant m ON m.id = o.merchant_id
            LEFT JOIN payment p ON p.id = (SELECT MAX(p2.id) FROM payment p2 WHERE p2.order_id = o.id)
            WHERE o.created_at &gt;= #{start} AND o.created_at &lt;= #{end}
            <if test="merchantId != null"> AND o.merchant_id = #{merchantId}</if>
            <if test="status != null and status != ''"> AND o.status = #{status}</if>
            <if test="keyword != null and keyword != ''">
              AND (o.order_no LIKE CONCAT('%', #{keyword}, '%') OR o.consignee LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY o.created_at DESC, o.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<OrderExportRow> selectOrderRows(@Param("merchantId") Long merchantId,
                                         @Param("status") String status,
                                         @Param("keyword") String keyword,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("limit") int limit);
}
