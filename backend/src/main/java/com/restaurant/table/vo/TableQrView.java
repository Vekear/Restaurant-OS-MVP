package com.restaurant.table.vo;

import java.time.LocalDateTime;

/** 桌台二维码视图（{@code docs/02-api.md §2.7}）。 */
public record TableQrView(Long tableId,
                          String tableNo,
                          Integer qrVersion,
                          String qrContent,
                          String qrUrl,
                          String qrImageUrl,
                          LocalDateTime signedAt) {
}
