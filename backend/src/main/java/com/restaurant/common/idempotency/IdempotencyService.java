package com.restaurant.common.idempotency;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.Result;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.idempotency.mapper.IdempotencyRecordMapper;
import com.restaurant.common.util.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 幂等执行器（架构 §16.7）：
 * <ol>
 *   <li>校验 {@code Idempotency-Key}（8–64）；</li>
 *   <li>Redis {@code idem:{userId}:{key}} 保存请求体哈希，同 key 不同请求体 → 40904；</li>
 *   <li>数据库 {@code idempotency_record} 唯一键保证只执行一次，成功响应体用于回放
 *       （表结构无 request_hash 列，哈希落在 Redis sidecar）；</li>
 *   <li>回放前校验记录归属：数据库命中必须按 {@code (user_id, idempotency_key)} 查，
 *       其他用户复用同一 key → 40904 且绝不回放他人响应体（H-02 修复）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int WAIT_TIMES = 20;

    private static final long WAIT_MILLIS = 100L;

    private final IdempotencyRecordMapper idempotencyRecordMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final AppProperties appProperties;

    /**
     * 以幂等方式执行一次业务动作。
     *
     * @param key      客户端 Idempotency-Key
     * @param endpoint 逻辑端点标识，例如 {@code POST /api/v1/orders}
     * @param userId   当前用户
     * @param request  请求体（用于计算哈希，需可序列化且字段顺序稳定）
     * @param action   首次执行时的业务动作
     */
    public <T> IdempotentResult<T> execute(String key, String endpoint, Long userId, Object request,
                                           Supplier<T> action) {
        validateKey(key);
        String requestHash = hash(request);
        String redisKey = CacheKeys.idempotencyReplayKey(userId, key);
        Duration ttl = Duration.ofHours(appProperties.getIdempotency().getTtlHours());

        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, requestHash, ttl);
        if (Boolean.FALSE.equals(first)) {
            String existingHash = stringRedisTemplate.opsForValue().get(redisKey);
            if (existingHash != null && !existingHash.equals(requestHash)) {
                throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key 已被不同的请求使用");
            }
        }

        IdempotencyRecord record = findOwnedRecord(key, userId);
        if (record == null) {
            // 数据库唯一键是全局的：若该 key 已被其他用户占用，视为冲突，不回放他人响应体
            IdempotencyRecord otherUserRecord = findRecordByKey(key);
            if (otherUserRecord != null) {
                throw conflict("Idempotency-Key 已被其他用户使用");
            }
        }
        if (record != null && "SUCCESS".equals(record.getStatus())) {
            return new IdempotentResult<>(null, record.getResponseBody());
        }
        if (record != null && "PROCESSING".equals(record.getStatus())) {
            IdempotencyRecord finished = waitForSuccess(key, userId);
            if (finished == null) {
                throw conflict("相同请求正在处理中，请稍后重试");
            }
            return new IdempotentResult<>(null, finished.getResponseBody());
        }
        if (record != null && "FAILED".equals(record.getStatus())) {
            // 上次执行失败：允许使用同一 key 重试
            idempotencyRecordMapper.deleteById(record.getId());
            record = null;
        }
        if (record == null) {
            record = newRecord(key, userId, endpoint);
            try {
                idempotencyRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                IdempotencyRecord concurrent = findRecordByKey(key);
                if (concurrent == null) {
                    throw conflict("相同请求正在处理中，请稍后重试");
                }
                if (!Objects.equals(concurrent.getUserId(), userId)) {
                    throw conflict("Idempotency-Key 已被其他用户使用");
                }
                if ("SUCCESS".equals(concurrent.getStatus())) {
                    return new IdempotentResult<>(null, concurrent.getResponseBody());
                }
                IdempotencyRecord finished = waitForSuccess(key, userId);
                if (finished != null) {
                    return new IdempotentResult<>(null, finished.getResponseBody());
                }
                throw conflict("相同请求正在处理中，请稍后重试");
            }
        }

        try {
            T data = action.get();
            String body = objectMapper.writeValueAsString(Result.success(data));
            record.setStatus("SUCCESS");
            record.setResponseCode(Result.SUCCESS_CODE);
            record.setResponseBody(body);
            record.setExpiresAt(LocalDateTime.now().plus(ttl));
            idempotencyRecordMapper.updateById(record);
            return new IdempotentResult<>(data, null);
        } catch (RuntimeException e) {
            idempotencyRecordMapper.deleteById(record.getId());
            stringRedisTemplate.delete(redisKey);
            throw e;
        } catch (JsonProcessingException e) {
            idempotencyRecordMapper.deleteById(record.getId());
            stringRedisTemplate.delete(redisKey);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "响应序列化失败");
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()
                || key.length() < ApiConstants.IDEMPOTENCY_KEY_MIN_LENGTH
                || key.length() > ApiConstants.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BizException(ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key 缺失或长度不在 8-64 之间");
        }
    }

    /** 仅返回属于当前用户的幂等记录；这是回放/删除/等待的唯一入口。 */
    private IdempotencyRecord findOwnedRecord(String key, Long userId) {
        return idempotencyRecordMapper.selectOne(Wrappers.<IdempotencyRecord>lambdaQuery()
                .eq(IdempotencyRecord::getUserId, userId)
                .eq(IdempotencyRecord::getIdempotencyKey, key));
    }

    /** 全局按 key 查记录，仅用于判断“key 是否已被其他用户占用”，不会用于回放。 */
    private IdempotencyRecord findRecordByKey(String key) {
        return idempotencyRecordMapper.selectOne(Wrappers.<IdempotencyRecord>lambdaQuery()
                .eq(IdempotencyRecord::getIdempotencyKey, key));
    }

    private IdempotencyRecord waitForSuccess(String key, Long userId) {
        for (int i = 0; i < WAIT_TIMES; i++) {
            IdempotencyRecord record = findOwnedRecord(key, userId);
            if (record != null && "SUCCESS".equals(record.getStatus())) {
                return record;
            }
            if (record == null || "FAILED".equals(record.getStatus())) {
                return null;
            }
            try {
                Thread.sleep(WAIT_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private BizException conflict(String message) {
        return new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, message);
    }

    private IdempotencyRecord newRecord(String key, Long userId, String endpoint) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(key);
        record.setUserId(userId);
        record.setEndpoint(endpoint);
        record.setStatus("PROCESSING");
        record.setExpiresAt(LocalDateTime.now().plusHours(appProperties.getIdempotency().getTtlHours()));
        return record;
    }

    private String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(json);
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "幂等请求体序列化失败");
        }
    }
}
