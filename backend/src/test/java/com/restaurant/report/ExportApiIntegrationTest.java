package com.restaurant.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T10 验收：订单/统计 xlsx 导出可下载、Content-Type 正确、文件可被 POI 解析，
 * 且商家看板统计数字与实际完成订单一致。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExportApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @BeforeEach
    void cleanRateLimitKeys() {
        Set<String> keys = redisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void merchantOrderAndSalesExportAreParseableAndDashboardNumbersMatch() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        String customerToken = login("customer", "customer123");

        String dishName = "导出菜品" + suffix();
        long dishId = createDish(merchantToken, dishName, "30.00", 20);
        long orderId = createOrder(customerToken, dishId, 2);
        String orderNo = JsonPath.read(get("/api/v1/orders/" + orderId, customerToken), "$.data.orderNo");

        String today = LocalDate.now(ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE);
        Map<String, Object> before = summary(merchantToken, today);

        payOrder(customerToken, orderId);
        String accept = post("/api/v1/merchant/orders/" + orderId + "/accept", null, merchantToken);
        assertThat(codeOf(accept)).isZero();
        assertThat(codeOf(post("/api/v1/merchant/orders/" + orderId + "/prepare", null, merchantToken))).isZero();
        assertThat(codeOf(post("/api/v1/merchant/orders/" + orderId + "/deliver", null, merchantToken))).isZero();
        String complete = post("/api/v1/merchant/orders/" + orderId + "/complete", null, merchantToken);
        assertThat(codeOf(complete)).isZero();
        assertThat(JsonPath.read(complete, "$.data.status").toString()).isEqualTo("COMPLETED");

        Map<String, Object> after = summary(merchantToken, today);
        assertThat(new BigDecimal(after.get("revenue").toString()).subtract(new BigDecimal(before.get("revenue").toString())))
                .isEqualByComparingTo("65.00");
        // 订单在 before 快照前已创建，orderCount 不变；完成状态与营业额增加。
        assertThat(((Number) after.get("orderCount")).longValue() - ((Number) before.get("orderCount")).longValue()).isZero();
        assertThat(((Number) after.get("completedCount")).longValue() - ((Number) before.get("completedCount")).longValue()).isEqualTo(1L);
        assertThat(new BigDecimal(after.get("avgOrderAmount").toString())).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        String trend = get("/api/v1/merchant/dashboard/trend?startDate=" + today + "&endDate=" + today, merchantToken);
        assertThat(codeOf(trend)).isZero();
        assertThat(((Number) JsonPath.read(trend, "$.data.totalOrderCount")).longValue()).isGreaterThanOrEqualTo(1L);

        ResponseEntity<byte[]> ordersExport = download(
                "/api/v1/merchant/export/orders?startDate=" + today + "&endDate=" + today, merchantToken);
        assertXlsx(ordersExport);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(ordersExport.getBody()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> header = headerIndex(sheet.getRow(0));
            assertThat(header).containsKeys("订单号", "订单实付金额", "模拟扣款金额", "下单时间", "菜品明细");
            Row target = findRow(sheet, header.get("订单号"), orderNo);
            assertThat(target).isNotNull();
            assertThat(target.getCell(header.get("订单实付金额")).getNumericCellValue()).isEqualTo(65.00);
            assertThat(target.getCell(header.get("模拟扣款金额")).getNumericCellValue()).isEqualTo(0.01);
            assertThat(target.getCell(header.get("菜品明细")).getStringCellValue()).contains(dishName);
        }

        ResponseEntity<byte[]> salesExport = download(
                "/api/v1/merchant/reports/export?startDate=" + today + "&endDate=" + today, merchantToken);
        assertXlsx(salesExport);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(salesExport.getBody()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheetName(0)).isEqualTo("汇总");
            assertThat(workbook.getSheetName(1)).isEqualTo("趋势");
            assertThat(workbook.getSheetName(2)).isEqualTo("热销");
            assertThat(workbook.getSheetName(3)).isEqualTo("订单类型");
        }

        String adminToken = login("admin", "admin123");
        ResponseEntity<byte[]> adminOrders = download(
                "/api/v1/admin/orders/export?startDate=" + today + "&endDate=" + today, adminToken);
        assertXlsx(adminOrders);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(adminOrders.getBody()))) {
            Map<String, Integer> header = headerIndex(workbook.getSheetAt(0).getRow(0));
            assertThat(header).containsKeys("订单号", "商家", "订单实付金额", "模拟扣款金额");
            assertThat(findRow(workbook.getSheetAt(0), header.get("订单号"), orderNo)).isNotNull();
        }

        ResponseEntity<byte[]> adminStats = download(
                "/api/v1/admin/reports/export?startDate=" + today + "&endDate=" + today, adminToken);
        assertXlsx(adminStats);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(adminStats.getBody()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
            assertThat(workbook.getSheetName(0)).isEqualTo("平台汇总");
            assertThat(workbook.getSheetName(4)).isEqualTo("商家排行");
        }

        ResponseEntity<String> openapi = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(openapi.getStatusCode().value()).isEqualTo(200);
        assertThat(openapi.getBody()).contains("/api/v1/merchant/export/orders");
        assertThat(openapi.getBody()).contains("/api/v1/admin/orders/export");

        assertThat(download("/api/v1/admin/orders/export", customerToken).getStatusCode().value()).isEqualTo(403);
        assertThat(download("/api/v1/admin/stats/export", merchantToken).getStatusCode().value()).isEqualTo(403);
    }

    private Map<String, Object> summary(String token, String date) throws Exception {
        String body = get("/api/v1/merchant/dashboard/summary?date=" + date, token);
        assertThat(codeOf(body)).isZero();
        Map<String, Object> map = objectMapper.convertValue(JsonPath.read(body, "$.data"), Map.class);
        return map;
    }

    private void assertXlsx(ResponseEntity<byte[]> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String contentType = response.getHeaders().getContentType() == null ? ""
                : response.getHeaders().getContentType().toString();
        assertThat(contentType).contains("spreadsheetml.sheet");
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("attachment").contains(".xlsx");
        byte[] body = response.getBody();
        assertThat(body).isNotNull().hasSizeGreaterThan(100);
        assertThat(new byte[]{body[0], body[1], body[2], body[3]}).containsExactly((byte) 'P', (byte) 'K', (byte) 3, (byte) 4);
    }

    private Map<String, Integer> headerIndex(Row headerRow) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            index.put(headerRow.getCell(i).getStringCellValue(), i);
        }
        return index;
    }

    private Row findRow(Sheet sheet, int column, String value) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getCell(column) != null
                    && value.equals(row.getCell(column).getStringCellValue())) {
                return row;
            }
        }
        return null;
    }

    private long createDish(String token, String name, String price, int stock) throws Exception {
        String suffix = suffix();
        Map<String, Object> body = Map.of("categoryId", 1, "name", name, "price", price, "stock", stock,
                "description", "导出测试菜品" + suffix);
        String response = post("/api/v1/merchant/dishes", body, token);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private long createOrder(String token, long dishId, int quantity) throws Exception {
        Map<String, Object> body = Map.of("merchantId", 1, "addressId", 1,
                "items", new Object[]{Map.of("dishId", dishId, "quantity", quantity)},
                "remark", "导出测试");
        HttpHeaders headers = jsonHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        assertThat(codeOf(response.getBody())).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private void payOrder(String token, long orderId) throws Exception {
        HttpHeaders headers = jsonHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        String create = rest.exchange("/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("orderId", orderId)), headers),
                String.class).getBody();
        assertThat(codeOf(create)).isZero();
        String payNo = JsonPath.read(create, "$.data.payNo");
        HttpHeaders payHeaders = jsonHeaders(token);
        payHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        String paid = rest.exchange("/api/v1/payments/" + payNo + "/mock-pay", HttpMethod.POST,
                new HttpEntity<>("{}", payHeaders), String.class).getBody();
        assertThat(codeOf(paid)).isZero();
    }

    private ResponseEntity<byte[]> download(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private String login(String username, String password) throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("username", username, "password", password)),
                        jsonHeaders(null)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private String get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }

    private String post(String path, Object body, String token) throws Exception {
        HttpHeaders headers = jsonHeaders(token);
        String payload = body == null ? "{}" : objectMapper.writeValueAsString(body);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class).getBody();
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private int codeOf(String body) {
        return JsonPath.read(body, "$.code");
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
