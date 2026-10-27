package com.restaurant.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.TimeZone;

/**
 * Jackson 全局序列化配置（契约：{@code docs/02-api.md §0.1}）。
 *
 * <ul>
 *   <li>时区固定 {@code Asia/Shanghai}；{@link LocalDateTime} 输出 ISO-8601 带偏移，
 *       例如 {@code 2026-10-28T10:00:00+08:00}；仅日期输出 {@code yyyy-MM-dd}；</li>
 *   <li>BigDecimal 以普通十进制数值输出（禁止科学计数法），金额精度由 DECIMAL(10,2) 保证；</li>
 *   <li>反序列化对时间宽松：接受 ISO 带偏移、ISO 本地时间与 {@code yyyy-MM-dd HH:mm:ss}；</li>
 *   <li>忽略未知字段，避免前端多传字段导致 400。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    public static final String DATE_PATTERN = "yyyy-MM-dd";

    public static final String TIME_PATTERN = "HH:mm:ss";

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter OFFSET_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private static final List<DateTimeFormatter> INPUT_DATE_TIME_FORMATTERS = List.of(
            OFFSET_DATE_TIME_FORMATTER, LOCAL_DATE_TIME_FORMATTER, LEGACY_DATE_TIME_FORMATTER
    );

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer restaurantJacksonCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone(DEFAULT_ZONE));
            // 显式按类型注册，避免自定义序列化器 handledType 推断失败（Jackson2ObjectMapperBuilder 限制）
            builder.serializerByType(LocalDateTime.class, new IsoOffsetLocalDateTimeSerializer());
            builder.serializerByType(LocalDate.class,
                    new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
            builder.serializerByType(LocalTime.class,
                    new LocalTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
            builder.serializerByType(BigDecimal.class, new BigDecimalPlainSerializer());
            builder.deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
            builder.deserializerByType(LocalDate.class,
                    new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(
                            DateTimeFormatter.ofPattern(DATE_PATTERN)));
            builder.deserializerByType(LocalTime.class,
                    new com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer(
                            DateTimeFormatter.ofPattern(TIME_PATTERN)));
            builder.featuresToDisable(
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    SerializationFeature.FAIL_ON_EMPTY_BEANS,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
            );
        };
    }

    /**
     * LocalDateTime → ISO-8601 带偏移（固定 +08:00）。
     */
    public static class IsoOffsetLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                generator.writeNull();
                return;
            }
            generator.writeString(value.atZone(DEFAULT_ZONE).toOffsetDateTime()
                    .format(OFFSET_DATE_TIME_FORMATTER));
        }
    }

    /**
     * 宽松解析 LocalDateTime：ISO 带偏移 / ISO 本地 / yyyy-MM-dd HH:mm:ss。
     */
    public static class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            String trimmed = text.trim();
            for (DateTimeFormatter formatter : INPUT_DATE_TIME_FORMATTERS) {
                try {
                    TemporalAccessor parsed = formatter.parseBest(trimmed,
                            java.time.OffsetDateTime::from, LocalDateTime::from);
                    if (parsed instanceof java.time.OffsetDateTime offsetDateTime) {
                        return offsetDateTime.atZoneSameInstant(DEFAULT_ZONE).toLocalDateTime();
                    }
                    return (LocalDateTime) parsed;
                } catch (DateTimeException ignored) {
                    // 尝试下一种格式
                }
            }
            throw InvalidFormatException.from(parser,
                    "时间格式不合法，应为 ISO-8601 或 " + DATE_TIME_PATTERN, trimmed, LocalDateTime.class);
        }
    }

    /**
     * BigDecimal 普通十进制输出：{@code 12.30} 不会被写成 {@code 1.23E+1}。
     */
    public static class BigDecimalPlainSerializer extends JsonSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                generator.writeNull();
                return;
            }
            generator.writeNumber(value.toPlainString());
        }
    }
}
