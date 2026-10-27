package com.restaurant.common.exception;

import java.util.List;

/**
 * 字段级校验失败时 {@code Result.data} 的结构：{@code {"errors":[{"field":..,"message":..}]}}。
 *
 * @param errors 校验错误列表（最多返回 5 条）
 */
public record ValidationErrors(List<ValidationErrorItem> errors) {
}
