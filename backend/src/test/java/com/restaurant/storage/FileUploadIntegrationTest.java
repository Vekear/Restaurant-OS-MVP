package com.restaurant.storage;

import com.jayway.jsonpath.JsonPath;
import com.restaurant.common.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地文件存储集成测试：真实 multipart 上传 → 落盘 → file_object → /files/** 可读回；
 * 同时覆盖非法类型/大小/魔数、未登录 401、policy 不含密钥、local 模式 confirm 拒绝。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FileUploadIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String POLICY = "/api/v1/files/policy";

    private static final String UPLOAD = "/api/v1/files/upload";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AppProperties appProperties;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> rateKeys = stringRedisTemplate.keys("rate:*");
        if (rateKeys != null && !rateKeys.isEmpty()) {
            stringRedisTemplate.delete(rateKeys);
        }
    }

    @Test
    void policyReportsLocalBackendModeWithoutSecrets() {
        String token = login();
        ResponseEntity<String> policy = get(POLICY + "?bizType=dish&ext=png", token);
        assertThat(policy.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(policy.getBody(), "$.data.storageType")).isEqualTo("local");
        assertThat((String) JsonPath.read(policy.getBody(), "$.data.uploadMode")).isEqualTo("backend");
        assertThat((String) JsonPath.read(policy.getBody(), "$.data.uploadUrl"))
                .isEqualTo("/api/v1/files/upload");
        assertThat(policy.getBody())
                .doesNotContain("accessKeySecret")
                .doesNotContain("AccessKeySecret")
                .doesNotContain("signature")
                .doesNotContain("policy");

        ResponseEntity<String> badBizType = get(POLICY + "?bizType=evil&ext=png", token);
        assertThat(badBizType.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badBizType)).isEqualTo(40001);

        ResponseEntity<String> badExt = get(POLICY + "?bizType=dish&ext=txt", token);
        assertThat(badExt.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badExt)).isEqualTo(40003);
    }

    @Test
    void uploadWritesFileToDiskAndStaticUrlReadsItBack() throws Exception {
        String token = login();
        byte[] png = pngBytes(160);
        ResponseEntity<String> uploaded = upload(token, "/api/v1/files/upload?bizType=dish",
                "test.png", "image/png", png);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(uploaded)).isZero();
        String objectKey = JsonPath.read(uploaded.getBody(), "$.data.objectKey");
        String url = JsonPath.read(uploaded.getBody(), "$.data.url");
        long fileId = ((Number) JsonPath.read(uploaded.getBody(), "$.data.id")).longValue();
        assertThat((String) JsonPath.read(uploaded.getBody(), "$.data.storageType")).isEqualTo("local");
        assertThat((String) JsonPath.read(uploaded.getBody(), "$.data.bizType")).isEqualTo("dish");
        assertThat(((Number) JsonPath.read(uploaded.getBody(), "$.data.size")).longValue())
                .isEqualTo(png.length);
        assertThat(objectKey).startsWith("dish/").endsWith(".png");
        assertThat(url).isEqualTo("/files/" + objectKey);
        assertThat(uploaded.getBody()).doesNotContain("accessKeySecret");

        Path root = Paths.get(appProperties.getStorage().getLocal().getRoot()).toAbsolutePath().normalize();
        Path onDisk = root.resolve(objectKey);
        assertThat(Files.exists(onDisk)).isTrue();
        assertThat(Files.readAllBytes(onDisk)).containsExactly(png);

        ResponseEntity<byte[]> served = restTemplate.getForEntity(url, byte[].class);
        assertThat(served.getStatusCode().value()).isEqualTo(200);
        assertThat(served.getBody()).containsExactly(png);

        ResponseEntity<String> metadata = get("/api/v1/files/" + fileId, token);
        assertThat(metadata.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(metadata.getBody(), "$.data.objectKey")).isEqualTo(objectKey);

        // 兼容任务书中的 POST /api/v1/files 路径（与 /upload 等价）
        ResponseEntity<String> alias = upload(token, "/api/v1/files?bizType=avatar",
                "avatar.png", "image/png", pngBytes(64));
        assertThat(alias.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(alias.getBody(), "$.data.bizType")).isEqualTo("avatar");
    }

    @Test
    void uploadRejectsInvalidTypeSizeAndMagicAndRequiresLogin() {
        String token = login();

        ResponseEntity<String> anonymous = upload(null, "/api/v1/files/upload?bizType=dish",
                "x.png", "image/png", pngBytes(32));
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> badType = upload(token, "/api/v1/files/upload?bizType=dish",
                "x.txt", "text/plain", "hello".getBytes());
        assertThat(badType.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badType)).isEqualTo(40003);

        ResponseEntity<String> wrongMagic = upload(token, "/api/v1/files/upload?bizType=dish",
                "x.png", "image/png", "not-an-image".getBytes());
        assertThat(wrongMagic.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(wrongMagic)).isEqualTo(40003);

        ResponseEntity<String> badBizType = upload(token, "/api/v1/files/upload?bizType=evil",
                "x.png", "image/png", pngBytes(32));
        assertThat(badBizType.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badBizType)).isEqualTo(40001);

        byte[] oversize = pngBytes(5 * 1024 * 1024 + 1);
        ResponseEntity<String> tooLarge = upload(token, "/api/v1/files/upload?bizType=dish",
                "big.png", "image/png", oversize);
        assertThat(tooLarge.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(tooLarge)).isEqualTo(40003);

        ResponseEntity<String> confirmInLocalMode = exchange(HttpMethod.POST, "/api/v1/files/confirm",
                Map.of("bizType", "dish", "objectKey", "dish/2026/10/x.png", "size", 10,
                        "contentType", "image/png"), token);
        assertThat(confirmInLocalMode.getStatusCode().value()).isEqualTo(500);
        assertThat(codeOf(confirmInLocalMode)).isEqualTo(50002);
    }

    private String login() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, LOGIN,
                Map.of("username", "customer", "password", "customer123"), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> upload(String token, String path, String filename,
                                          String contentType, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private int codeOf(ResponseEntity<String> response) {
        return ((Number) JsonPath.read(response.getBody(), "$.code")).intValue();
    }

    private static byte[] pngBytes(int size) {
        byte[] bytes = new byte[Math.max(size, 8)];
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4E;
        bytes[3] = 0x47;
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;
        for (int i = 8; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }
}
