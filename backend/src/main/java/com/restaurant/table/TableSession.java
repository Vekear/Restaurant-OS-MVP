package com.restaurant.table;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 桌台会话 {@code table_session}（{@code docs/02-api.md §10}）。
 *
 * <p>{@code session_token} 是顾客后续堂食下单的凭据（请求头 {@code X-Table-Session}），
 * 仅在扫码/绑桌响应中下发一次；会话关闭后立即失效。
 */
@Getter
@Setter
@TableName("table_session")
public class TableSession extends BaseEntity {

    private Long tableId;

    private Long merchantId;

    private String sessionToken;

    /** ACTIVE / CLOSED / EXPIRED。 */
    private String status;

    private Integer guestCount;

    /** 开桌用户 ID（字符串存储，兼容匿名场景）。 */
    private String openedBy;

    private Long currentOrderId;

    private LocalDateTime openedAt;

    private LocalDateTime lastOrderAt;

    private LocalDateTime closedAt;

    private LocalDateTime expireAt;

    @Version
    private Integer version;
}
