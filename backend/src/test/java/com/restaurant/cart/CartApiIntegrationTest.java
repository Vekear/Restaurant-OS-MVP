package com.restaurant.cart;

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
 * 购物车接口集成测试：清空/加购合并/改数量/失效行不计金额/库存与下架校验/幂等删除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CartApiIntegrationTest {

    private static final String CART = "/api/v1/cart";

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
    void cartLifecycleMergesQuantitiesAndClears() throws Exception {
        String token = login();
        String merchantToken = loginMerchant();

        ResponseEntity<String> cleared = exchange(CART, HttpMethod.DELETE, null, token);
        assertThat(cleared.getStatusCode().value()).isEqualTo(200);
        assertThat(intAt(cleared, "$.data.totalQuantity")).isZero();
        assertThat(decimalAt(cleared, "$.data.totalAmount")).isZero();
        assertThat((List<Object>) JsonPath.read(cleared.getBody(), "$.data.items")).isEmpty();
        assertThat(intAt(cleared, "$.data.maxItems")).isEqualTo(50);

        long dishId = createDish(merchantToken, "购物车菜品" + suffix(), "30.00", 5);

        ResponseEntity<String> added = exchange(CART + "/items", HttpMethod.POST, addBody(dishId, 2), token);
        assertThat(added.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(added)).isZero();
        assertThat(intAt(added, "$.data.totalQuantity")).isEqualTo(2);
        assertThat(decimalAt(added, "$.data.totalAmount")).isEqualTo(60.0);
        assertThat((List<Object>) JsonPath.read(added.getBody(), "$.data.items")).hasSize(1);
        assertThat(intAt(added, "$.data.items[0].quantity")).isEqualTo(2);
        assertThat((Boolean) JsonPath.read(added.getBody(), "$.data.items[0].available")).isTrue();

        ResponseEntity<String> merged = exchange(CART + "/items", HttpMethod.POST, addBody(dishId, 1), token);
        assertThat(intAt(merged, "$.data.totalQuantity")).isEqualTo(3);
        assertThat((List<Object>) JsonPath.read(merged.getBody(), "$.data.items")).hasSize(1);
        assertThat(intAt(merged, "$.data.items[0].quantity")).isEqualTo(3);

        ResponseEntity<String> patched = exchange(CART + "/items/" + dishId, HttpMethod.PATCH, qtyBody(4), token);
        assertThat(patched.getStatusCode().value()).isEqualTo(200);
        assertThat(intAt(patched, "$.data.totalQuantity")).isEqualTo(4);
        assertThat(decimalAt(patched, "$.data.totalAmount")).isEqualTo(120.0);

        ResponseEntity<String> overStock = exchange(CART + "/items/" + dishId, HttpMethod.PATCH, qtyBody(6), token);
        assertThat(overStock.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(overStock)).isEqualTo(40901);
        assertThat((String) JsonPath.read(overStock.getBody(), "$.message")).contains("库存不足");

        ResponseEntity<String> removed = exchange(CART + "/items/" + dishId, HttpMethod.DELETE, null, token);
        assertThat(removed.getStatusCode().value()).isEqualTo(200);
        assertThat(intAt(removed, "$.data.totalQuantity")).isZero();

        ResponseEntity<String> removedAgain = exchange(CART + "/items/" + dishId, HttpMethod.DELETE, null, token);
        assertThat(removedAgain.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(removedAgain)).isZero();
    }

    @Test
    void invalidRowsStayButDoNotCountAndValidationIsEnforced() throws Exception {
        String token = login();
        String merchantToken = loginMerchant();
        exchange(CART, HttpMethod.DELETE, null, token);

        long dishId = createDish(merchantToken, "失效菜品" + suffix(), "20.00", 2);
        ResponseEntity<String> added = exchange(CART + "/items", HttpMethod.POST, addBody(dishId, 2), token);
        assertThat(decimalAt(added, "$.data.totalAmount")).isEqualTo(40.0);

        ResponseEntity<String> stockSet = exchange("/api/v1/merchant/dishes/" + dishId + "/stock",
                HttpMethod.PUT, Map.of("stock", 0), merchantToken);
        assertThat(stockSet.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> cart = exchange(CART, HttpMethod.GET, null, token);
        assertThat(intAt(cart, "$.data.totalQuantity")).isEqualTo(2);
        assertThat(intAt(cart, "$.data.invalidCount")).isEqualTo(1);
        assertThat(decimalAt(cart, "$.data.totalAmount")).isZero();
        assertThat((Boolean) JsonPath.read(cart.getBody(), "$.data.items[0].available")).isFalse();

        ResponseEntity<String> addWhenEmpty = exchange(CART + "/items", HttpMethod.POST, addBody(dishId, 1), token);
        assertThat(addWhenEmpty.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(addWhenEmpty)).isEqualTo(40901);

        long offShelfId = createDish(merchantToken, "下架菜品" + suffix(), "15.00", 10);
        ResponseEntity<String> offShelf = exchange("/api/v1/merchant/dishes/" + offShelfId + "/status",
                HttpMethod.PATCH, Map.of("status", "OFF_SHELF"), merchantToken);
        assertThat(offShelf.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> addOffShelf = exchange(CART + "/items", HttpMethod.POST, addBody(offShelfId, 1), token);
        assertThat(addOffShelf.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(addOffShelf)).isEqualTo(40918);
        assertThat((String) JsonPath.read(addOffShelf.getBody(), "$.message")).contains("下架");

        ResponseEntity<String> missingRow = exchange(CART + "/items/999999999", HttpMethod.PATCH, qtyBody(1), token);
        assertThat(missingRow.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(missingRow)).isEqualTo(40401);

        ResponseEntity<String> badQuantity = exchange(CART + "/items", HttpMethod.POST, addBody(dishId, 100), token);
        assertThat(badQuantity.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badQuantity)).isEqualTo(40001);
        assertThat((List<Object>) JsonPath.read(badQuantity.getBody(), "$.data.errors")).isNotEmpty();

        exchange(CART, HttpMethod.DELETE, null, token);
    }

    private long createDish(String merchantToken, String name, String price, int stock) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", 1);
        body.put("name", name);
        body.put("price", new BigDecimal(price));
        body.put("stock", stock);
        ResponseEntity<String> response = exchange("/api/v1/merchant/dishes", HttpMethod.POST, body, merchantToken);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private Map<String, Object> addBody(long dishId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dishId", dishId);
        body.put("quantity", quantity);
        return body;
    }

    private Map<String, Object> qtyBody(int quantity) {
        return Map.of("quantity", quantity);
    }

    private String login() throws Exception {
        return login("customer", "customer123");
    }

    private String loginMerchant() throws Exception {
        return login("merchant", "merchant123");
    }

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> response = exchange("/api/v1/auth/login", HttpMethod.POST, body, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
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

    private int intAt(ResponseEntity<String> response, String path) {
        Number value = JsonPath.read(response.getBody(), path);
        return value == null ? -1 : value.intValue();
    }

    private double decimalAt(ResponseEntity<String> response, String path) {
        Number value = JsonPath.read(response.getBody(), path);
        return value == null ? -1 : value.doubleValue();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
