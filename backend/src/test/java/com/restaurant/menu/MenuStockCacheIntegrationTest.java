package com.restaurant.menu;

import com.jayway.jsonpath.JsonPath;
import com.restaurant.menu.mapper.DishMapper;
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
 * 库存/菜品变更后公开菜单缓存失效集成测试（T41）。
 *
 * <p>真实 Tomcat + MySQL restaurant_mvp_test + Redis db15。覆盖：
 * 下单扣库存、取消回补、商家设置/调整库存、库存不足回滚（缓存不得被清空）。
 * 断言既检查 HTTP 返回值，也检查 Redis 缓存键是否被清理/重新生成，并对照数据库 stock。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MenuStockCacheIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String MERCHANT_DISHES = "/api/v1/merchant/dishes";

    private static final String PUBLIC_DISHES = "/api/v1/menu/dishes";

    private static final String ORDERS = "/api/v1/orders";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DishMapper dishMapper;

    @BeforeEach
    void clearCaches() {
        deleteKeys("menu:*");
        deleteKeys("rate:*");
    }

    @Test
    void orderDeductionEvictsCachedDishDetailAndList() {
        String merchantToken = login("merchant", "merchant123");
        String customerToken = login("customer", "customer123");
        String dishName = "扣减缓存菜品" + suffix();
        long dishId = createDish(merchantToken, dishName, "30.00", 10);

        // 预热详情与分页缓存
        assertThat(publicDetailStock(dishId)).isEqualTo(10);
        assertThat(publicListStock(dishId, dishName)).isEqualTo(10);
        assertThat(stringRedisTemplate.hasKey("menu:dish:" + dishId)).isTrue();
        assertThat(listCacheKeys()).isNotEmpty();

        ResponseEntity<String> created = createOrder(customerToken, dishId, 2);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();

        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(8);
        assertThat(publicDetailStock(dishId)).isEqualTo(8);
        assertThat(publicListStock(dishId, dishName)).isEqualTo(8);
        // GET 详情会重新写入缓存（值来自数据库）
        assertThat(stringRedisTemplate.hasKey("menu:dish:" + dishId)).isTrue();
    }

    @Test
    void cancelRestoresStockAndRefreshesCachedDetail() {
        String merchantToken = login("merchant", "merchant123");
        String customerToken = login("customer", "customer123");
        String dishName = "回补缓存菜品" + suffix();
        long dishId = createDish(merchantToken, dishName, "30.00", 5);
        assertThat(publicDetailStock(dishId)).isEqualTo(5);

        long orderId = createOrderAndGetId(customerToken, dishId, 2);
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(3);
        assertThat(publicDetailStock(dishId)).isEqualTo(3);

        ResponseEntity<String> cancelled = exchange(ORDERS + "/" + orderId + "/cancel",
                HttpMethod.POST, Map.of("reason", "缓存回补验证"), customerToken, null);
        assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(cancelled)).isZero();

        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(5);
        assertThat(publicDetailStock(dishId)).isEqualTo(5);
    }

    @Test
    void merchantStockSetAndAdjustEvictPublicMenuCache() {
        String merchantToken = login("merchant", "merchant123");
        String dishName = "商家改库存菜品" + suffix();
        long dishId = createDish(merchantToken, dishName, "30.00", 10);
        assertThat(publicDetailStock(dishId)).isEqualTo(10);
        assertThat(publicListStock(dishId, dishName)).isEqualTo(10);

        ResponseEntity<String> set = exchange(MERCHANT_DISHES + "/" + dishId + "/stock",
                HttpMethod.PUT, Map.of("stock", 7), merchantToken, null);
        assertThat(set.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(set.getBody(), "$.data.stock")).intValue()).isEqualTo(7);
        assertThat(publicDetailStock(dishId)).isEqualTo(7);
        assertThat(publicListStock(dishId, dishName)).isEqualTo(7);

        ResponseEntity<String> adjust = exchange(MERCHANT_DISHES + "/" + dishId + "/stock/adjust",
                HttpMethod.POST, Map.of("delta", -2, "reason", "破损"), merchantToken, null);
        assertThat(adjust.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(adjust.getBody(), "$.data.stock")).intValue()).isEqualTo(5);
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(5);
        assertThat(publicDetailStock(dishId)).isEqualTo(5);
        assertThat(publicListStock(dishId, dishName)).isEqualTo(5);
    }

    @Test
    void stockInsufficientRollbackKeepsCacheIntact() {
        String merchantToken = login("merchant", "merchant123");
        String customerToken = login("customer", "customer123");
        String dishName = "回滚缓存菜品" + suffix();
        long dishId = createDish(merchantToken, dishName, "30.00", 1);
        assertThat(publicDetailStock(dishId)).isEqualTo(1);
        assertThat(publicListStock(dishId, dishName)).isEqualTo(1);
        Set<String> detailKeysBefore = stringRedisTemplate.keys("menu:dish:" + dishId);
        Set<String> listKeysBefore = listCacheKeys();
        assertThat(detailKeysBefore).isNotEmpty();
        assertThat(listKeysBefore).isNotEmpty();

        ResponseEntity<String> failed = createOrder(customerToken, dishId, 5);
        assertThat(failed.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(failed)).isEqualTo(40901);
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(1);

        // 事务回滚 → 不得清空缓存；缓存仍返回旧值（= 数据库值）
        assertThat(stringRedisTemplate.hasKey("menu:dish:" + dishId)).isTrue();
        assertThat(listCacheKeys()).isNotEmpty();
        assertThat(publicDetailStock(dishId)).isEqualTo(1);
        assertThat(publicListStock(dishId, dishName)).isEqualTo(1);
    }

    private long createOrderAndGetId(String customerToken, long dishId, int quantity) {
        ResponseEntity<String> created = createOrder(customerToken, dishId, quantity);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();
        return ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
    }

    private ResponseEntity<String> createOrder(String customerToken, long dishId, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("merchantId", 1);
        body.put("addressId", 1);
        body.put("items", List.of(Map.of("dishId", dishId, "quantity", quantity)));
        body.put("remark", "T41 缓存验证");
        return exchange(ORDERS, HttpMethod.POST, body, customerToken, "t41-" + suffix());
    }

    private int publicDetailStock(long dishId) {
        ResponseEntity<String> detail = exchange(PUBLIC_DISHES + "/" + dishId, HttpMethod.GET, null, null, null);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(detail)).isZero();
        return ((Number) JsonPath.read(detail.getBody(), "$.data.stock")).intValue();
    }

    private int publicListStock(long dishId, String dishName) {
        ResponseEntity<String> list = exchange(
                PUBLIC_DISHES + "?merchantId=1&keyword=" + dishName + "&page=1&size=100",
                HttpMethod.GET, null, null, null);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        List<Number> ids = JsonPath.read(list.getBody(), "$.data.list[*].id");
        List<Number> stocks = JsonPath.read(list.getBody(), "$.data.list[*].stock");
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).longValue() == dishId) {
                return stocks.get(i).intValue();
            }
        }
        throw new AssertionError("公开菜单未找到菜品 " + dishId);
    }

    private Set<String> listCacheKeys() {
        Set<String> keys = stringRedisTemplate.keys("menu:dishes:1:*");
        return keys == null ? Set.of() : keys;
    }

    private long createDish(String token, String name, String price, int stock) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("categoryId", 1);
        payload.put("name", name);
        payload.put("price", price);
        payload.put("stock", stock);
        ResponseEntity<String> response = exchange(MERCHANT_DISHES, HttpMethod.POST, payload, token, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private String login(String username, String password) {
        ResponseEntity<String> response = exchange(LOGIN, HttpMethod.POST,
                Map.of("username", username, "password", password), null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private int codeOf(ResponseEntity<String> response) {
        return ((Number) JsonPath.read(response.getBody(), "$.code")).intValue();
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token,
                                            String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
