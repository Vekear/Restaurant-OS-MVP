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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存并发集成测试：验证原子 UPDATE 不会把库存扣成负数，且低库存判定正确。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String CATEGORIES = "/api/v1/merchant/categories";

    private static final String DISHES = "/api/v1/merchant/dishes";

    private static final String LOW_STOCK = "/api/v1/merchant/inventory/low-stock";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearCache() {
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
    void concurrentDecrementsNeverGoNegativeAndLowStockIsReported() throws Exception {
        String token = login();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        long categoryId = createCategory(token, "库存分类" + suffix);
        long dishId = createDish(token, categoryId, "并发菜品" + suffix, 50);

        int threads = 10;
        int delta = -5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                start.await();
                return exchange(DISHES + "/" + dishId + "/stock/adjust", HttpMethod.POST,
                        Map.of("delta", delta, "reason", "并发测试"), token).getStatusCode().value();
            });
        }
        List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();
        start.countDown();
        for (Future<Integer> future : futures) {
            assertThat(future.get()).isEqualTo(200);
        }
        pool.shutdownNow();

        ResponseEntity<String> detail = get(DISHES + "/" + dishId, token);
        assertThat(((Number) JsonPath.read(detail.getBody(), "$.data.stock")).intValue()).isZero();
        assertThat((Boolean) JsonPath.read(detail.getBody(), "$.data.soldOut")).isTrue();

        ResponseEntity<String> oversell = exchange(DISHES + "/" + dishId + "/stock/adjust",
                HttpMethod.POST, Map.of("delta", -1), token);
        assertThat(oversell.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(oversell)).isEqualTo(40901);

        ResponseEntity<String> increase = exchange(DISHES + "/" + dishId + "/stock/adjust",
                HttpMethod.POST, Map.of("delta", 10), token);
        assertThat(increase.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(increase.getBody(), "$.data.stock")).intValue()).isEqualTo(10);
        assertThat((Boolean) JsonPath.read(increase.getBody(), "$.data.lowStock")).isFalse();

        ResponseEntity<String> setLow = exchange(DISHES + "/" + dishId + "/stock",
                HttpMethod.PUT, Map.of("stock", 3), token);
        assertThat(setLow.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(setLow.getBody(), "$.data.stock")).intValue()).isEqualTo(3);
        assertThat((Boolean) JsonPath.read(setLow.getBody(), "$.data.lowStock")).isTrue();

        ResponseEntity<String> lowStock = get(LOW_STOCK + "?threshold=5&size=100", token);
        assertThat(lowStock.getStatusCode().value()).isEqualTo(200);
        List<Number> ids = JsonPath.read(lowStock.getBody(), "$.data.list[*].id");
        assertThat(ids.stream().map(Number::longValue)).contains(dishId);

        long zeroDelta = exchange(DISHES + "/" + dishId + "/stock/adjust", HttpMethod.POST,
                Map.of("delta", 0), token).getStatusCode().value();
        assertThat(zeroDelta).isEqualTo(400);
    }

    private String login() {
        ResponseEntity<String> response = exchange(LOGIN, HttpMethod.POST,
                Map.of("username", "merchant", "password", "merchant123"), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private long createCategory(String token, String name) {
        ResponseEntity<String> response = exchange(CATEGORIES, HttpMethod.POST,
                Map.of("name", name), token);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private long createDish(String token, long categoryId, String name, int stock) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("categoryId", categoryId);
        payload.put("name", name);
        payload.put("price", "10.00");
        payload.put("stock", stock);
        ResponseEntity<String> response = exchange(DISHES, HttpMethod.POST, payload, token);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
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
