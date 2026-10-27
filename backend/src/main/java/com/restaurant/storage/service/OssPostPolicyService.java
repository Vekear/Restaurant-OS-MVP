package com.restaurant.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云 OSS Post Policy 签发（{@code docs/01-architecture.md §15}，ADR-0006）。
 *
 * <p>签名 = Base64(HMAC-SHA1(AccessKeySecret, policyBase64))；policy 限制 bucket、
 * content-length-range、对象键前缀与 Content-Type。AccessKeySecret 只参与服务端签名，
 * 永不出现在日志或响应中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssPostPolicyService {

    /** OSS 要求的 policy 过期时间格式。 */
    private static final DateTimeFormatter EXPIRATION_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter EXPIRE_AT_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final String HMAC_SHA1 = "HmacSHA1";

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final AppProperties appProperties;

    private final ObjectMapper objectMapper;

    /** OSS 关键配置是否齐全（齐全才启用直传，否则降级 local）。 */
    public boolean configured() {
        AppProperties.Storage.Oss oss = appProperties.getStorage().getOss();
        return StringUtils.hasText(oss.getEndpoint())
                && StringUtils.hasText(oss.getBucket())
                && StringUtils.hasText(oss.getAccessKeyId())
                && StringUtils.hasText(oss.getAccessKeySecret());
    }

    /** 生成一次直传所需的策略与签名。 */
    public Policy buildPolicy(String bizType, String ext) {
        AppProperties.Storage.Oss oss = appProperties.getStorage().getOss();
        if (!configured()) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "OSS 未配置，已降级本地存储");
        }
        String normalizedExt = FileValidation.requireExtension(ext);
        String contentType = FileValidation.contentTypeForExtension(normalizedExt);
        String dir = normalizeDir(oss.getDir()) + bizType + "/" + FileValidation.monthPath() + "/";
        String objectKey = dir + FileValidation.uuid() + "." + normalizedExt;
        long maxBytes = appProperties.getUpload().getMaxSize().toBytes();
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60L, oss.getExpireSeconds()));
        String expiration = EXPIRATION_FORMAT.format(expiresAt);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("expiration", expiration);
        document.put("conditions", List.of(
                Map.of("bucket", oss.getBucket()),
                List.of("content-length-range", 1, maxBytes),
                List.of("starts-with", "$key", dir),
                List.of("eq", "$content-type", contentType)));
        String policyJson;
        try {
            policyJson = objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            log.error("生成 OSS Policy 失败", e);
            throw new BizException(ErrorCode.STORAGE_ERROR, "上传策略生成失败，请重试");
        }
        String encodedPolicy = Base64.getEncoder()
                .encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
        String signature = hmacSha1Base64(oss.getAccessKeySecret(), encodedPolicy);
        String expireAt = OffsetDateTime.ofInstant(expiresAt, ZONE_SHANGHAI).format(EXPIRE_AT_FORMAT);
        return new Policy(publicHost(), dir, objectKey, encodedPolicy, signature,
                oss.getAccessKeyId(), expireAt);
    }

    /** OSS 公网访问前缀：优先 public-base-url，否则按 bucket.endpoint 推导。 */
    public String publicHost() {
        AppProperties.Storage.Oss oss = appProperties.getStorage().getOss();
        if (StringUtils.hasText(oss.getPublicBaseUrl())) {
            return trimTrailingSlash(oss.getPublicBaseUrl());
        }
        String endpoint = oss.getEndpoint() == null ? "" : oss.getEndpoint().trim();
        endpoint = endpoint.replaceFirst("^https?://", "");
        return "https://" + oss.getBucket() + "." + trimTrailingSlash(endpoint);
    }

    /** Base64(HMAC-SHA1(secret, data))，单独暴露便于单元测试验证签名算法。 */
    public static String hmacSha1Base64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "OSS 签名失败，请检查配置");
        }
    }

    /** 对象键目录：确保以 / 结尾且不以 / 开头。 */
    static String normalizeDir(String dir) {
        String normalized = dir == null ? "" : dir.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.isEmpty() && !normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /** 一次直传的签名结果（accessKeyId 可公开，secret 不在此结构内）。 */
    public record Policy(String host, String dir, String objectKey, String policy, String signature,
                         String accessKeyId, String expireAt) {
    }
}
