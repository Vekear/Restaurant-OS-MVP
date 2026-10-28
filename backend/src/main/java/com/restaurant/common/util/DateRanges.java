package com.restaurant.common.util;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 统计/导出使用的日期区间工具（Asia/Shanghai 自然日）。
 *
 * <p>口径见 {@code docs/02-api.md §2.9 TBD-06}：区间为 {@code [startDate 00:00:00, endDate 23:59:59.999]}。
 */
public final class DateRanges {

    /** 业务时区。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 查询参数日期格式。 */
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private DateRanges() {
    }

    /** 今天（业务时区）。 */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * 解析可选的 {@code yyyy-MM-dd} 参数，空串返回 {@code null}。
     *
     * @throws BizException 40001 格式错误
     */
    public static LocalDate parseOptional(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 格式应为 yyyy-MM-dd");
        }
    }

    /**
     * 校验并补全区间：空值使用默认值；{@code start > end} 或跨度超限抛 40001。
     */
    public static Range resolve(LocalDate start, LocalDate end, LocalDate defaultStart, LocalDate defaultEnd,
                                int maxRangeDays) {
        LocalDate resolvedStart = start == null ? defaultStart : start;
        LocalDate resolvedEnd = end == null ? defaultEnd : end;
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "startDate 不能晚于 endDate");
        }
        long days = ChronoUnit.DAYS.between(resolvedStart, resolvedEnd) + 1;
        if (maxRangeDays > 0 && days > maxRangeDays) {
            throw new BizException(ErrorCode.PARAM_INVALID, "查询区间不能超过 " + maxRangeDays + " 天");
        }
        return new Range(resolvedStart, resolvedEnd);
    }

    /** 当天 00:00:00。 */
    public static LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    /** 当天 23:59:59.999999999。 */
    public static LocalDateTime endOf(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MAX);
    }

    /** 文件名日期 {@code yyyyMMdd}。 */
    public static String fileDate(LocalDate date) {
        return date.format(FILE_DATE_FORMAT);
    }

    /** 闭区间日期。 */
    public record Range(LocalDate start, LocalDate end) {

        public LocalDateTime startTime() {
            return startOf(start);
        }

        public LocalDateTime endTime() {
            return endOf(end);
        }
    }
}
