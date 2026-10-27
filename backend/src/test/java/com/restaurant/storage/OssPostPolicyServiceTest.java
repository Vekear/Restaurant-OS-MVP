package com.restaurant.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.storage.service.OssPostPolicyService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OSS Post Policy 签名单元测试（不启动 Spring）：policy 限制项、HMAC-SHA1 签名、secret 不外泄。
 */
class OssPostPolicyServiceTest {

    private static final String SECRET = "SECRET-DEMO-0123456789";

    @Test
    void buildPolicySignsRestrictionsAndNeverLeaksSecret() {
        AppProperties appProperties = ossProperties();
        OssPostPolicyService service = new OssPostPolicyService(appProperties, new ObjectMapper());
        assertThat(service.configured()).isTrue();

        OssPostPolicyService.Policy policy = service.buildPolicy("dish", "png");

        assertThat(policy.host()).isEqualTo("https://demo-bucket.oss-cn-hangzhou.aliyuncs.com");
        assertThat(policy.dir()).startsWith("biz/dish/").endsWith("/");
        assertThat(policy.objectKey()).startsWith(policy.dir()).endsWith(".png");
        assertThat(policy.accessKeyId()).isEqualTo("AKID-DEMO");
        assertThat(policy.expireAt()).contains("+08:00");

        String decoded = new String(Base64.getDecoder().decode(policy.policy()), StandardCharsets.UTF_8);
        assertThat(decoded)
                .contains("demo-bucket")
                .contains("content-length-range")
                .contains("starts-with")
                .contains("image/png")
                .doesNotContain(SECRET);
        assertThat(policy.signature())
                .isEqualTo(OssPostPolicyService.hmacSha1Base64(SECRET, policy.policy()));
        assertThat(policy.policy()).doesNotContain(SECRET);
    }

    @Test
    void publicBaseUrlOverridesDerivedHost() {
        AppProperties appProperties = ossProperties();
        appProperties.getStorage().getOss().setPublicBaseUrl("https://cdn.example.com/");
        OssPostPolicyService service = new OssPostPolicyService(appProperties, new ObjectMapper());

        assertThat(service.buildPolicy("avatar", "jpg").host()).isEqualTo("https://cdn.example.com");
    }

    @Test
    void missingCredentialsDowngradeAndRejectPolicy() {
        AppProperties appProperties = ossProperties();
        appProperties.getStorage().getOss().setAccessKeySecret("");
        OssPostPolicyService service = new OssPostPolicyService(appProperties, new ObjectMapper());

        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.buildPolicy("dish", "png"))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getCode())
                        .isEqualTo(ErrorCode.STORAGE_ERROR.getCode()));
    }

    private AppProperties ossProperties() {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setType("oss");
        appProperties.getStorage().getOss().setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        appProperties.getStorage().getOss().setBucket("demo-bucket");
        appProperties.getStorage().getOss().setAccessKeyId("AKID-DEMO");
        appProperties.getStorage().getOss().setAccessKeySecret(SECRET);
        appProperties.getStorage().getOss().setDir("biz/");
        appProperties.getStorage().getOss().setExpireSeconds(600);
        appProperties.getStorage().getOss().setPublicBaseUrl("");
        return appProperties;
    }
}
