package com.restaurant.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson 契约单元测试（不依赖 Spring 容器/MySQL/Redis）。
 *
 * <p>验证 {@link JacksonConfig} 的自定义序列化器按 {@code docs/02-api.md §0.1} 输出：
 * LocalDateTime → ISO-8601 带 +08:00 偏移；BigDecimal → 普通十进制；日期/时间格式固定；
 * 反序列化兼容 ISO 带偏移、ISO 本地与 {@code yyyy-MM-dd HH:mm:ss}。
 */
class JacksonConfigTest {

    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder.modules(new JavaTimeModule());
        new JacksonConfig().restaurantJacksonCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void serializesDateTimeAmountDateAndTimePerContract() throws Exception {
        String json = MAPPER.writeValueAsString(new Payload(
                LocalDateTime.of(2026, 10, 28, 10, 0, 0),
                new BigDecimal("1230.50"),
                LocalDate.of(2026, 10, 28),
                LocalTime.of(9, 5, 3)));

        assertThat(json)
                .contains("\"createdAt\":\"2026-10-28T10:00:00+08:00\"")
                .contains("\"amount\":1230.50")
                .contains("\"date\":\"2026-10-28\"")
                .contains("\"time\":\"09:05:03\"");
        assertThat(json).doesNotContain("E+");
    }

    @Test
    void decimalKeepsTwoDecimalsWithoutScientificNotation() throws Exception {
        assertThat(MAPPER.writeValueAsString(new BigDecimal("0.01"))).isEqualTo("0.01");
        assertThat(MAPPER.writeValueAsString(new BigDecimal("12.30"))).isEqualTo("12.30");
    }

    @Test
    void deserializesFlexibleDateTimeInputs() throws Exception {
        Payload legacy = MAPPER.readValue("{\"createdAt\":\"2026-10-28 10:00:00\"}", Payload.class);
        Payload offset = MAPPER.readValue("{\"createdAt\":\"2026-10-28T10:00:00+08:00\"}", Payload.class);
        Payload isoLocal = MAPPER.readValue("{\"createdAt\":\"2026-10-28T10:00:00\"}", Payload.class);
        LocalDateTime expected = LocalDateTime.of(2026, 10, 28, 10, 0, 0);

        assertThat(legacy.createdAt()).isEqualTo(expected);
        assertThat(offset.createdAt()).isEqualTo(expected);
        assertThat(isoLocal.createdAt()).isEqualTo(expected);
    }

    @Test
    void ignoresUnknownProperties() throws Exception {
        Payload payload = MAPPER.readValue("{\"createdAt\":\"2026-10-28T10:00:00+08:00\",\"unknown\":1}",
                Payload.class);
        assertThat(payload.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 28, 10, 0, 0));
    }

    /** 测试用载体：覆盖金额、日期、时间、日期时间四类契约字段。 */
    record Payload(LocalDateTime createdAt, BigDecimal amount, LocalDate date, LocalTime time) {
    }
}
