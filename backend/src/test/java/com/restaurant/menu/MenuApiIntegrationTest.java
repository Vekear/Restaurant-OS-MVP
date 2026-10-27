package com.restaurant.menu;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公开菜单 + 商家分类/菜品集成测试（真实 Tomcat + MySQL restaurant_mvp_test + Redis db15）。
 *
 * <p>覆盖：公开菜单只返回 ON_SALE、分页默认值/校验、商家 CRUD 冲突码、缓存命中与写后立即失效、
 * 公开配置与店铺信息。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MenuApiIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String CATEGORIES = "/api/v1/merchant/categories";

    private static final String DISHES = "/api/v1/merchant/dishes";

    private static final String PUBLIC_CATEGORIES = "/api/v1/menu/categories";

    private static final String PUBLIC_DISHES = "/api/v1/menu/dishes";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearMenuAndRateLimitCache() {
        Set<String> keys = stringRedisTemplate.keys("menu:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        Set<String> rateKeys = stringRedisTemplate.keys("rate:*");
        if (rateKeys != null && !rateKeys.isEmpty()) {
            stringRedisTemplate.delete(rateKeys);
        }
    }

    @Test
    void publicMenuOnlyExposesOnSaleDishesAndCacheIsEvictedOnChange() {
        String merchantToken = login("merchant", "merchant123");
        long categoryId = createCategory(merchantToken, "测试分类" + suffix());
        long dishId = createDish(merchantToken, categoryId, "测试菜品" + suffix(), "12.34", 20);

        ResponseEntity<String> categories = get(PUBLIC_CATEGORIES + "?merchantId=1", null);
        assertThat(categories.getStatusCode().value()).isEqualTo(200);
        List<Number> categoryIds = JsonPath.read(categories.getBody(), "$.data[*].id");
        assertThat(categoryIds.stream().map(Number::longValue)).contains(categoryId);
        assertThat((Boolean) stringRedisTemplate.hasKey("menu:categories:1")).isTrue();

        ResponseEntity<String> dishes = get(
                PUBLIC_DISHES + "?merchantId=1&categoryId=" + categoryId, null);
        assertThat(dishes.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(dishes.getBody(), "$.data.total")).longValue()).isEqualTo(1L);
        assertThat((String) JsonPath.read(dishes.getBody(), "$.data.list[0].name")).startsWith("测试菜品");
        assertThat(((Number) JsonPath.read(dishes.getBody(), "$.data.list[0].price")).doubleValue())
                .isEqualTo(12.34);
        assertThat((Boolean) JsonPath.read(dishes.getBody(), "$.data.list[0].soldOut")).isFalse();

        ResponseEntity<String> detail = get(PUBLIC_DISHES + "/" + dishId, null);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(detail.getBody(), "$.data.reviewCount")).longValue()).isZero();
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.categoryName")).startsWith("测试分类");

        // 下架后公开菜单立即不可见（缓存失效）
        Map<String, String> offShelf = Map.of("status", "OFF_SHELF");
        assertThat(exchange(DISHES + "/" + dishId + "/status", HttpMethod.PATCH, offShelf, merchantToken)
                .getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> afterOffShelf = get(
                PUBLIC_DISHES + "?merchantId=1&categoryId=" + categoryId, null);
        assertThat(((Number) JsonPath.read(afterOffShelf.getBody(), "$.data.total")).longValue()).isZero();

        ResponseEntity<String> offShelfDetail = get(PUBLIC_DISHES + "/" + dishId, null);
        assertThat(offShelfDetail.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(offShelfDetail)).isEqualTo(40401);
    }

    @Test
    void publicMenuAppliesDefaultsAndValidatesParameters() {
        ResponseEntity<String> defaults = get(PUBLIC_DISHES + "?merchantId=1", null);
        assertThat(defaults.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(defaults.getBody(), "$.data.page")).longValue()).isEqualTo(1L);
        assertThat(((Number) JsonPath.read(defaults.getBody(), "$.data.size")).longValue()).isEqualTo(12L);

        ResponseEntity<String> tooLarge = get(PUBLIC_DISHES + "?merchantId=1&size=101", null);
        assertThat(tooLarge.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(tooLarge)).isEqualTo(40001);

        ResponseEntity<String> unknownMerchant = get(PUBLIC_DISHES + "?merchantId=999999", null);
        assertThat(unknownMerchant.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(unknownMerchant)).isEqualTo(40401);

        ResponseEntity<String> unknownDish = get(PUBLIC_DISHES + "/999999", null);
        assertThat(unknownDish.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(unknownDish)).isEqualTo(40401);
    }

    @Test
    void merchantCategoryCrudEnforcesConflictsAndRole() {
        String merchantToken = login("merchant", "merchant123");
        String name = "分类CRUD" + suffix();
        long categoryId = createCategory(merchantToken, name);

        ResponseEntity<String> duplicate = exchange(CATEGORIES, HttpMethod.POST,
                Map.of("name", name), merchantToken);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(duplicate)).isEqualTo(40914);

        long dishId = createDish(merchantToken, categoryId, "待删除菜品" + suffix(), "9.90", 5);
        ResponseEntity<String> deleteNotEmpty = exchange(CATEGORIES + "/" + categoryId,
                HttpMethod.DELETE, null, merchantToken);
        assertThat(deleteNotEmpty.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(deleteNotEmpty)).isEqualTo(40915);

        assertThat(exchange(DISHES + "/" + dishId, HttpMethod.DELETE, null, merchantToken)
                .getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> deleted = exchange(CATEGORIES + "/" + categoryId,
                HttpMethod.DELETE, null, merchantToken);
        assertThat(deleted.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(deleted.getBody(), "$.data.deleted")).isTrue();

        ResponseEntity<String> missing = exchange(CATEGORIES + "/" + categoryId,
                HttpMethod.PUT, Map.of("name", name + "x"), merchantToken);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40401);

        String customerToken = login("customer", "customer123");
        ResponseEntity<String> forbidden = get(CATEGORIES, customerToken);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(forbidden)).isEqualTo(40301);
    }

    @Test
    void merchantDishValidationAndPublicCacheReuse() {
        String merchantToken = login("merchant", "merchant123");
        long categoryId = createCategory(merchantToken, "分类校验" + suffix());

        Map<String, Object> invalidPrice = new HashMap<>();
        invalidPrice.put("categoryId", categoryId);
        invalidPrice.put("name", "原价错误" + suffix());
        invalidPrice.put("price", "20.00");
        invalidPrice.put("originalPrice", "10.00");
        invalidPrice.put("stock", 10);
        ResponseEntity<String> rejected = exchange(DISHES, HttpMethod.POST, invalidPrice, merchantToken);
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(rejected)).isEqualTo(40001);

        long dishId = createDish(merchantToken, categoryId, "缓存菜品" + suffix(), "8.80", 30);
        ResponseEntity<String> first = get(PUBLIC_DISHES + "/" + dishId, null);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) stringRedisTemplate.hasKey("menu:dish:" + dishId)).isTrue();
        ResponseEntity<String> second = get(PUBLIC_DISHES + "/" + dishId, null);
        assertThat(second.getStatusCode().value()).isEqualTo(200);

        assertThat(exchange(DISHES + "/" + dishId, HttpMethod.DELETE, null, merchantToken)
                .getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> afterDelete = get(PUBLIC_DISHES + "/" + dishId, null);
        assertThat(afterDelete.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void publicConfigAndMerchantInfoAreServed() {
        ResponseEntity<String> config = get("/api/v1/public/config", null);
        assertThat(config.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(config.getBody(), "$.data.defaultMerchantId")).longValue())
                .isEqualTo(1L);
        assertThat((String) JsonPath.read(config.getBody(), "$.data.timezone")).isEqualTo("Asia/Shanghai");
        assertThat((String) JsonPath.read(config.getBody(), "$.data.storage.type")).isEqualTo("local");
        assertThat((String) JsonPath.read(config.getBody(), "$.data.storage.uploadMode")).isEqualTo("backend");
        assertThat(((Number) JsonPath.read(config.getBody(), "$.data.storage.maxSizeMb")).intValue())
                .isEqualTo(5);
        assertThat(JsonPath.read(config.getBody(), "$.data.mockPayment.chargeAmount").toString())
                .isEqualTo("0.01");
        assertThat((String) JsonPath.read(config.getBody(), "$.data.websocket.stompPath")).isEqualTo("/ws");

        ResponseEntity<String> merchant = get("/api/v1/merchants/1", null);
        assertThat(merchant.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(merchant.getBody(), "$.data.name")).isEqualTo("川味小馆");
        assertThat((String) JsonPath.read(merchant.getBody(), "$.data.businessStatus")).isEqualTo("OPEN");

        ResponseEntity<String> missing = get("/api/v1/merchants/999999", null);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40401);
    }

    private long createCategory(String token, String name) {
        ResponseEntity<String> response = exchange(CATEGORIES, HttpMethod.POST,
                Map.of("name", name, "sort", 9), token);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private long createDish(String token, long categoryId, String name, String price, int stock) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("categoryId", categoryId);
        payload.put("name", name);
        payload.put("price", price);
        payload.put("stock", stock);
        ResponseEntity<String> response = exchange(DISHES, HttpMethod.POST, payload, token);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private String login(String username, String password) {
        ResponseEntity<String> response = exchange(LOGIN, HttpMethod.POST,
                Map.of("username", username, "password", password), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private int codeOf(ResponseEntity<String> response) {
        return ((Number) JsonPath.read(response.getBody(), "$.code")).intValue();
    }

    private ResponseEntity<String> get(String path, String token) {
        return exchange(path, HttpMethod.GET, null, token);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
