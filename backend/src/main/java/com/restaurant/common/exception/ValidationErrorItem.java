package com.restaurant.common.exception;

import jakarta.validation.constraints.NotBlank;

/**
 * 字段级校验错误项（{@code docs/02-api.md §0.2} 中 {@code data.errors[]} 的元素）。
 *
 * @param field   字段名（与请求 JSON 字段一致）
 * @param message 可直接展示的中文提示
 */
public record ValidationErrorItem(@NotBlank String field, String message) {
}
