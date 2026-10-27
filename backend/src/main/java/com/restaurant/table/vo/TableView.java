package com.restaurant.table.vo;

import java.time.LocalDateTime;

/** 商家桌台视图（{@code docs/02-api.md §2.7}）。 */
public record TableView(Long id,
                        Long merchantId,
                        String tableNo,
                        String name,
                        Integer capacity,
                        String zone,
                        String status,
                        Long currentSessionId,
                        Integer qrVersion,
                        String qrUrl,
                        String qrImageUrl,
                        Integer version,
                        LocalDateTime createdAt) {
}
