package com.restaurant.storage.vo;

import java.time.LocalDateTime;

/**
 * 文件对象视图（{@code docs/02-api.md §2.8}）。
 */
public record FileObjectView(Long id,
                             String bizType,
                             String objectKey,
                             String url,
                             String storageType,
                             long size,
                             String contentType,
                             String originalName,
                             LocalDateTime createdAt) {
}
