package com.restaurant.common.idempotency;

import com.baomidou.mybatisplus.annotation.TableName;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 幂等记录 {@code idempotency_record}：{@code idempotency_key} 全局唯一，
 * 首次成功响应体快照用于回放；请求体哈希存 Redis（表结构无 hash 列，见架构 §13）。
 */
@Getter
@Setter
@TableName("idempotency_record")
public class IdempotencyRecord extends BaseEntity {

    private String idempotencyKey;

    private Long userId;

    private String endpoint;

    /** PROCESSING / SUCCESS / FAILED。 */
    private String status;

    private Long orderId;

    private Integer responseCode;

    private String responseBody;

    private LocalDateTime expiresAt;
}
