package com.restaurant.table;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 桌台 {@code table_info}（{@code docs/01-architecture.md §10}、{@code docs/02-api.md §17}）。
 *
 * <p>扫码/绑桌通过 {@code version} 乐观锁抢占：仅当状态为 {@code FREE} 且版本一致时才能写入
 * {@code OCCUPIED + current_session_id}，并发场景下只有一个请求成功。
 * 二维码签名为 {@code BASE64URL(HMAC_SHA256(qrSecret, "{id}.{qrSalt}"))}，重签时轮换 {@code qrSalt}
 * 并让 {@code qrVersion+1}，旧二维码立即失效。
 */
@Getter
@Setter
@TableName("table_info")
public class TableInfo extends BaseEntity {

    private Long merchantId;

    private String tableNo;

    private String name;

    private String zone;

    private Integer capacity;

    /** FREE / OCCUPIED。 */
    private String status;

    private Long currentSessionId;

    private String qrCodeUrl;

    private String qrSalt;

    private Integer qrVersion;

    private LocalDateTime qrSignedAt;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;
}
