package com.restaurant.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * 模拟支付网关：mock-pay 时以「外部回调」方式 POST 本应用 {@code /payments/mock/callback}，
 * 携带 HMAC-SHA256 签名；失败时由调用方降级为直接处理（ADR-0004）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPayGatewayClient {

    private final AppProperties appProperties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /**
     * 发送模拟回调。
     *
     * @return 网关（本应用）返回 2xx 时为 true
     */
    public boolean sendCallback(Object payload) {
        AppProperties.Payment payment = appProperties.getPayment();
        String baseUrl = payment.getMockCallbackBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("未配置 app.payment.mock-callback-base-url，跳过回调直接处理（降级）");
            return false;
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signature = hmacSha256Hex(payment.getCallbackSecret(),
                    timestamp + "\n" + nonce + "\n" + body);
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(trimTrailingSlash(baseUrl) + "/api/v1/payments/mock/callback"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("X-Pay-Timestamp", Long.toString(timestamp))
                    .header("X-Pay-Nonce", nonce)
                    .header("X-Pay-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!ok) {
                log.warn("模拟支付回调返回非 2xx: status={}, body={}", response.statusCode(), response.body());
            }
            return ok;
        } catch (Exception e) {
            log.warn("模拟支付回调失败，降级为直接处理: {}", e.getMessage());
            return false;
        }
    }

    public static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
