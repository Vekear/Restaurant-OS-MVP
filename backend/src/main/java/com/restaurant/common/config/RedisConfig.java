package com.restaurant.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * Redis 序列化配置。
 *
 * <p>key 统一用 String（便于用 {@code redis-cli} 排查），value 用 JSON 并写入 {@code @class}
 * 类型信息，保证缓存回读时能还原为原始类型（含 record 视图对象）。
 * 该 ObjectMapper 仅供缓存内部使用，不参与 HTTP 序列化。
 *
 * <p>默认类型信息采用「除自然标量/集合外全部写入」策略：
 * <ul>
 *   <li>BigDecimal / LocalDateTime 等自然标量不写类型 id（自定义序列化器不支持标量类型 id）；</li>
 *   <li>List / Map 等集合不写类型 id，避免 JDK 不可变集合实现类无法反序列化的问题；</li>
 *   <li>其余对象（含 final record）写 {@code @class}，保证 {@code @Cacheable} 命中时按原始类型还原。</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    private static final Set<Class<?>> NATURAL_TYPES = Set.of(
            String.class, Boolean.class, Character.class,
            Integer.class, Long.class, Short.class, Byte.class, Double.class, Float.class,
            BigDecimal.class, BigInteger.class,
            LocalDate.class, LocalTime.class, LocalDateTime.class, Instant.class,
            UUID.class
    );

    @Bean
    public GenericJackson2JsonRedisSerializer redisValueSerializer(ObjectMapper objectMapper) {
        ObjectMapper cacheMapper = objectMapper.copy();
        PolymorphicTypeValidator validator =
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build();
        ObjectMapper.DefaultTypeResolverBuilder typer =
                new CacheTypeResolverBuilder(ObjectMapper.DefaultTyping.EVERYTHING, validator);
        typer.init(JsonTypeInfo.Id.CLASS, null);
        typer.inclusion(JsonTypeInfo.As.PROPERTY);
        cacheMapper.setDefaultTyping(typer);
        return new GenericJackson2JsonRedisSerializer(cacheMapper);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       GenericJackson2JsonRedisSerializer redisValueSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(redisValueSerializer);
        template.setHashValueSerializer(redisValueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 缓存专用类型解析器：自然标量、集合与数组不写类型 id，其余对象一律写。
     */
    private static final class CacheTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {

        private CacheTypeResolverBuilder(ObjectMapper.DefaultTyping typing,
                                         PolymorphicTypeValidator validator) {
            super(typing, validator);
        }

        @Override
        public boolean useForType(JavaType type) {
            Class<?> raw = type.getRawClass();
            if (NATURAL_TYPES.contains(raw) || raw.isArray()
                    || type.isCollectionLikeType() || type.isMapLikeType()) {
                return false;
            }
            // 兜底：java.* 容器实现类同样不写类型 id（如 Collections$UnmodifiableList）
            String name = raw.getName();
            if (name.startsWith("java.util.") || name.startsWith("java.lang.")) {
                return false;
            }
            return true;
        }
    }
}
