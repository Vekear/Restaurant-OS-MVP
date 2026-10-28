package com.restaurant.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 顾客优惠券接口集成测试：可领取列表、原子领取/重复领取、我的券、试算门槛与优惠额。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CouponApiIntegrationTest {

    private static final String COUPONS = "/api/v1/coupons";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void customerCanListClaimAndQuoteCoupons() throws Exception {
        String adminToken = login("admin", "admin123");
        long coupon50 = createCoupon(adminToken, "满50减10-" + suffix(), "50.00", "10.00");
        long coupon100 = createCoupon(adminToken, "满100减25-" + suffix(), "100.00", "25.00");

        String token = register();
        ResponseEntity<String> available = get(COUPONS + "?amount=60&page=1&size=50", token);
        assertThat(available.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(available)).isZero();
        List<Map<String, Object>> list = JsonPath.read(available.getBody(), "$.data.list");
        Map<String, Object> first = findCoupon(list, coupon50);
        assertThat((Boolean) first.get("claimed")).isFalse();
        assertThat((Boolean) first.get("claimable")).isTrue();
        assertThat((Boolean) first.get("usable")).isTrue();
        Map<String, Object> second = findCoupon(list, coupon100);
        assertThat((Boolean) second.get("usable")).isFalse();

        ResponseEntity<String> claimed = exchange(COUPONS + "/" + coupon50 + "/claim", HttpMethod.POST, null, token);
        assertThat(claimed.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(claimed)).isZero();
        assertThat((String) JsonPath.read(claimed.getBody(), "$.data.status")).isEqualTo("UNUSED");
        long userCouponId = ((Number) JsonPath.read(claimed.getBody(), "$.data.id")).longValue();

        ResponseEntity<String> duplicate = exchange(COUPONS + "/" + coupon50 + "/claim", HttpMethod.POST, null, token);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(duplicate)).isEqualTo(40912);
        assertThat((String) JsonPath.read(duplicate.getBody(), "$.message")).contains("已领取");

        ResponseEntity<String> afterClaim = get(COUPONS + "?amount=60&page=1&size=50", token);
        List<Map<String, Object>> afterList = JsonPath.read(afterClaim.getBody(), "$.data.list");
        Map<String, Object> claimedView = findCoupon(afterList, coupon50);
        assertThat((Boolean) claimedView.get("claimed")).isTrue();
        assertThat((Boolean) claimedView.get("claimable")).isFalse();

        ResponseEntity<String> mine = get(COUPONS + "/mine?page=1&size=50", token);
        assertThat(mine.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> mineList = JsonPath.read(mine.getBody(), "$.data.list");
        Map<String, Object> mineCoupon = mineList.stream()
                .filter(item -> ((Number) item.get("couponId")).longValue() == coupon50)
                .findFirst()
                .orElseThrow();
        assertThat((String) mineCoupon.get("status")).isEqualTo("UNUSED");
        assertThat((Boolean) mineCoupon.get("usable")).isTrue();
        assertThat(mineCoupon.get("reason")).isNull();

        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("merchantId", 1L);
        quote.put("totalAmount", new BigDecimal("60.00"));
        quote.put("userCouponId", userCouponId);
        ResponseEntity<String> quoted = exchange(COUPONS + "/quote", HttpMethod.POST, quote, token);
        assertThat(quoted.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(quoted)).isZero();
        assertThat(decimalAt(quoted, "$.data.discountAmount")).isEqualTo(10.0);
        assertThat(decimalAt(quoted, "$.data.deliveryFee")).isEqualTo(5.0);
        assertThat(decimalAt(quoted, "$.data.payAmount")).isEqualTo(55.0);
        assertThat(decimalAt(quoted, "$.data.minOrderAmount")).isEqualTo(20.0);
        assertThat((Boolean) JsonPath.read(quoted.getBody(), "$.data.reachMinOrder")).isTrue();
        assertThat((List<Object>) JsonPath.read(quoted.getBody(), "$.data.usableCoupons")).isNotEmpty();

        ResponseEntity<String> coupon50Quote = exchange(COUPONS + "/quote", HttpMethod.POST, quote, token);
        assertThat(decimalAt(coupon50Quote, "$.data.discountAmount")).isEqualTo(10.0);

        // 未达门槛的券被选中 → 40912
        Map<String, Object> belowThresholdClaim = new LinkedHashMap<>();
        belowThresholdClaim.put("merchantId", 1L);
        belowThresholdClaim.put("totalAmount", new BigDecimal("60.00"));
        ResponseEntity<String> quote100 = exchange(COUPONS + "/quote", HttpMethod.POST, belowThresholdClaim, token);
        assertThat(quote100.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> usable = JsonPath.read(quote100.getBody(), "$.data.usableCoupons");
        assertThat(usable).noneMatch(item -> ((Number) item.get("couponId")).longValue() == coupon100);

        Map<String, Object> selectedTooHigh = new LinkedHashMap<>();
        selectedTooHigh.put("merchantId", 1L);
        selectedTooHigh.put("totalAmount", new BigDecimal("60.00"));
        selectedTooHigh.put("userCouponId", 999999999L);
        ResponseEntity<String> badCoupon = exchange(COUPONS + "/quote", HttpMethod.POST, selectedTooHigh, token);
        assertThat(badCoupon.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(badCoupon)).isEqualTo(40912);

        Map<String, Object> belowMin = new LinkedHashMap<>();
        belowMin.put("merchantId", 1L);
        belowMin.put("totalAmount", new BigDecimal("10.00"));
        ResponseEntity<String> belowMinQuote = exchange(COUPONS + "/quote", HttpMethod.POST, belowMin, token);
        assertThat(belowMinQuote.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(belowMinQuote.getBody(), "$.data.reachMinOrder")).isFalse();
        assertThat(decimalAt(belowMinQuote, "$.data.discountAmount")).isZero();
    }

    @Test
    void couponEndpointsEnforceCustomerRoleAndArgumentValidation() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        ResponseEntity<String> forbidden = get(COUPONS, merchantToken);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(forbidden)).isEqualTo(40301);

        String token = register();
        Map<String, Object> badQuote = new LinkedHashMap<>();
        badQuote.put("merchantId", 1L);
        badQuote.put("totalAmount", new BigDecimal("0.00"));
        ResponseEntity<String> bad = exchange(COUPONS + "/quote", HttpMethod.POST, badQuote, token);
        assertThat(bad.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(bad)).isEqualTo(40001);

        ResponseEntity<String> badStatus = get(COUPONS + "/mine?status=WHAT", token);
        assertThat(badStatus.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badStatus)).isEqualTo(40001);

        ResponseEntity<String> unknownClaim = exchange(COUPONS + "/999999999/claim", HttpMethod.POST, null, token);
        assertThat(unknownClaim.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(unknownClaim)).isEqualTo(40401);
    }

    private Map<String, Object> findCoupon(List<Map<String, Object>> list, long couponId) {
        return list.stream()
                .filter(item -> ((Number) item.get("id")).longValue() == couponId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("优惠券未出现在列表中：" + couponId));
    }

    private long createCoupon(String adminToken, String name, String threshold, String discount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("thresholdAmount", new BigDecimal(threshold));
        body.put("discountAmount", new BigDecimal(discount));
        body.put("total", 100);
        body.put("startAt", "2026-01-01T00:00:00");
        body.put("endAt", "2030-12-31T23:59:59");
        ResponseEntity<String> response = exchange("/api/v1/admin/coupons", HttpMethod.POST, body, adminToken);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String register() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "cp" + suffix());
        body.put("password", "pass1234");
        ResponseEntity<String> response = exchange("/api/v1/auth/register", HttpMethod.POST, body, null);
        assertThat(codeOf(response)).isZero();
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> response = exchange("/api/v1/auth/login", HttpMethod.POST, body, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> get(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)), String.class);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token) throws Exception {
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        return restTemplate.exchange(path, method, new HttpEntity<>(payload, jsonHeaders(token)), String.class);
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private int codeOf(ResponseEntity<String> response) {
        try {
            Number code = JsonPath.read(response.getBody(), "$.code");
            return code == null ? -1 : code.intValue();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private double decimalAt(ResponseEntity<String> response, String path) {
        Number value = JsonPath.read(response.getBody(), path);
        return value == null ? -1 : value.doubleValue();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
