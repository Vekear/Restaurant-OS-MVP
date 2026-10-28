package com.restaurant.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T46 / docs/05-security.md M-01、M-02：
 * 生产配置 fail-fast 与 SpringDoc 分 profile 开关。
 *
 * <p>只做纯 Environment 级校验（不启动 Spring 上下文、不连数据库），
 * 保证 docker 缺密钥时在 Flyway/DataSource 之前失败且错误信息不泄露密钥值。
 */
class ProductionConfigFailFastTest {

    /** 随机风格强密钥（不含 dev-only / please-change 等占位标记）。 */
    private static final String STRONG_JWT_SECRET = "b7Fq2mZx9Lk4Wp8Rt3Yv6Nc1Hj5Ds0GaQe7Uo2Pi";
    private static final String STRONG_PAY_CALLBACK_SECRET = "p9K2sV7nQ4rT8wY3mB6cX1zL5hJ0gF2d";
    private static final String STRONG_TABLE_QR_SECRET = "q3H8jN5bV2xC7kM9pL4tR1wY6zS0dG3f";
    private static final String STRONG_DB_PASSWORD = "D9fK2mP7sX4vN8qL3wZ6bT1rY5cH0jG";
    private static final String LOCAL_STORAGE_TYPE = "local";

    private static MockEnvironment dockerEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("docker");
        environment.setProperty("app.storage.type", LOCAL_STORAGE_TYPE);
        return environment;
    }

    private static void strongSecrets(MockEnvironment environment) {
        environment.setProperty("app.jwt.secret", STRONG_JWT_SECRET);
        environment.setProperty("app.payment.callback-secret", STRONG_PAY_CALLBACK_SECRET);
        environment.setProperty("app.table.qr-secret", STRONG_TABLE_QR_SECRET);
        environment.setProperty("spring.datasource.password", STRONG_DB_PASSWORD);
    }

    @Test
    void dockerProfileWithoutSecretsFailsFastWithoutLeakingValues() {
        MockEnvironment environment = dockerEnvironment();

        Captured captured = postProcessExpectingFailure(environment);

        assertThat(captured.stderr())
                .contains("[FATAL]")
                .contains("app.jwt.secret", "JWT_SECRET")
                .contains("app.payment.callback-secret", "PAYMENT_CALLBACK_SECRET")
                .contains("app.table.qr-secret", "TABLE_QR_SECRET")
                .contains("spring.datasource.password", "DB_PASSWORD")
                .contains("profile")
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_JWT_SECRET)
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_PAY_CALLBACK_SECRET)
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_TABLE_QR_SECRET);
        assertThat(captured.failure().getMessage())
                .contains("app.jwt.secret")
                .contains("spring.datasource.password")
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_JWT_SECRET)
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_PAY_CALLBACK_SECRET)
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_DB_PASSWORD);
    }

    @Test
    void dockerProfileWithApplicationYmlDevelopmentDefaultsFailsFast() {
        MockEnvironment environment = dockerEnvironment();
        environment.setProperty("app.jwt.secret", ProductionConfigValidator.DEV_DEFAULT_JWT_SECRET);
        environment.setProperty("app.payment.callback-secret", ProductionConfigValidator.DEV_DEFAULT_PAY_CALLBACK_SECRET);
        environment.setProperty("app.table.qr-secret", ProductionConfigValidator.DEV_DEFAULT_TABLE_QR_SECRET);
        environment.setProperty("spring.datasource.password", ProductionConfigValidator.DEV_DEFAULT_DB_PASSWORD);

        ProductionConfigValidator.Validation validation = ProductionConfigValidator.validate(environment);

        assertThat(validation.failed()).isTrue();
        assertThat(validation.failedProperties()).isEqualTo(
                "app.jwt.secret, app.payment.callback-secret, app.table.qr-secret, spring.datasource.password");
        String report = ProductionConfigValidator.formatReport(validation);
        assertThat(report)
                .contains("开发默认值")
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_JWT_SECRET)
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_PAY_CALLBACK_SECRET)
                .doesNotContain(ProductionConfigValidator.DEV_DEFAULT_TABLE_QR_SECRET);
    }

    @Test
    void dockerProfileWithShortJwtSecretFailsFast() {
        MockEnvironment environment = dockerEnvironment();
        strongSecrets(environment);
        environment.setProperty("app.jwt.secret", "short-secret-123");

        ProductionConfigValidator.Validation validation = ProductionConfigValidator.validate(environment);

        assertThat(validation.failed()).isTrue();
        assertThat(validation.failedProperties()).isEqualTo("app.jwt.secret");
        assertThat(ProductionConfigValidator.formatReport(validation)).contains("长度不足 32 字节");
    }

    @Test
    void jwtSecretLengthBoundary() {
        MockEnvironment tooShort = dockerEnvironment();
        strongSecrets(tooShort);
        tooShort.setProperty("app.jwt.secret", "a".repeat(31));
        assertThat(ProductionConfigValidator.validate(tooShort).failed()).isTrue();

        MockEnvironment exactlyMinimum = dockerEnvironment();
        strongSecrets(exactlyMinimum);
        exactlyMinimum.setProperty("app.jwt.secret", "a".repeat(ProductionConfigValidator.JWT_MIN_BYTES));
        assertThat(ProductionConfigValidator.validate(exactlyMinimum).failed()).isFalse();
    }

    @Test
    void dockerProfileWithStrongSecretsPasses() {
        MockEnvironment environment = dockerEnvironment();
        strongSecrets(environment);

        ProductionConfigValidator.Validation validation = ProductionConfigValidator.validate(environment);

        assertThat(validation.failed()).isFalse();
        assertThat(validation.profiles()).containsExactly("docker");
        // 通过校验时不应抛异常
        new ProductionConfigFailFastEnvironmentPostProcessor().postProcessEnvironment(environment, null);
    }

    @Test
    void localAndTestProfilesKeepDevelopmentDefaults() {
        for (String profile : List.of("local", "test")) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            environment.setProperty("app.storage.type", LOCAL_STORAGE_TYPE);
            environment.setProperty("app.jwt.secret", ProductionConfigValidator.DEV_DEFAULT_JWT_SECRET);
            environment.setProperty("spring.datasource.password", ProductionConfigValidator.DEV_DEFAULT_DB_PASSWORD);

            assertThat(ProductionConfigValidator.validate(environment).failed())
                    .as("profile %s 应允许开发默认值", profile)
                    .isFalse();
        }
    }

    @Test
    void defaultProfileFallsBackToConfiguredLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.profiles.default", "local");
        environment.setProperty("app.storage.type", LOCAL_STORAGE_TYPE);

        assertThat(ProductionConfigValidator.effectiveProfiles(environment)).containsExactly("local");
        assertThat(ProductionConfigValidator.validate(environment).failed()).isFalse();
    }

    @Test
    void explicitActiveProfileWinsOverSpringProfilesDefault() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("docker");
        environment.setProperty("spring.profiles.default", "local");
        environment.setProperty("app.storage.type", LOCAL_STORAGE_TYPE);

        assertThat(ProductionConfigValidator.effectiveProfiles(environment)).containsExactly("docker");
        // 不能被 spring.profiles.default=local 误判为本地环境而跳过校验
        assertThat(ProductionConfigValidator.validate(environment).failed()).isTrue();
    }

    @Test
    void ossStorageTypeRequiresAllOssPropertiesInAnyProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        environment.setProperty("app.storage.type", "oss");
        environment.setProperty("app.oss.endpoint", "https://oss-cn-hangzhou.aliyuncs.com");
        environment.setProperty("app.oss.bucket", "restaurant-mvp");

        ProductionConfigValidator.Validation validation = ProductionConfigValidator.validate(environment);

        assertThat(validation.failed()).isTrue();
        assertThat(validation.failedProperties()).isEqualTo(
                "app.oss.access-key-id, app.oss.access-key-secret, app.oss.public-base-url");
        String report = ProductionConfigValidator.formatReport(validation);
        assertThat(report)
                .contains("app.oss.access-key-id", "OSS_ACCESS_KEY_ID")
                .contains("app.oss.public-base-url", "OSS_PUBLIC_BASE_URL")
                .doesNotContain("oss-cn-hangzhou.aliyuncs.com");
    }

    @Test
    void ossStorageTypeWithAllPropertiesPasses() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        environment.setProperty("app.storage.type", "oss");
        environment.setProperty("app.oss.endpoint", "https://oss-cn-hangzhou.aliyuncs.com");
        environment.setProperty("app.oss.bucket", "restaurant-mvp");
        environment.setProperty("app.oss.access-key-id", "LTAI-test-key-id");
        environment.setProperty("app.oss.access-key-secret", "test-key-secret-value");
        environment.setProperty("app.oss.public-base-url", "https://cdn.example.com");

        assertThat(ProductionConfigValidator.validate(environment).failed()).isFalse();
    }

    @Test
    void applicationYmlDevelopmentDefaultsStayInSyncWithValidator() throws Exception {
        assertThat(declaredDefault("app.jwt.secret"))
                .isEqualTo(ProductionConfigValidator.DEV_DEFAULT_JWT_SECRET);
        assertThat(declaredDefault("app.payment.callback-secret"))
                .isEqualTo(ProductionConfigValidator.DEV_DEFAULT_PAY_CALLBACK_SECRET);
        assertThat(declaredDefault("app.table.qr-secret"))
                .isEqualTo(ProductionConfigValidator.DEV_DEFAULT_TABLE_QR_SECRET);
        assertThat(declaredDefault("spring.datasource.password"))
                .isEqualTo(ProductionConfigValidator.DEV_DEFAULT_DB_PASSWORD);
    }

    private static Object declaredDefault(String property) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : sources) {
            Object raw = source.getProperty(property);
            if (raw != null) {
                return unwrapPlaceholderDefault(String.valueOf(raw));
            }
        }
        return null;
    }

    /** 从 ${ENV:default} 中取出 default，用于校验常量与 application.yml 未漂移。 */
    private static String unwrapPlaceholderDefault(String raw) {
        Matcher matcher = Pattern.compile("^\\$\\{[^:}]+:(.*)}$", Pattern.DOTALL).matcher(raw);
        return matcher.matches() ? matcher.group(1) : raw;
    }

    private static Captured postProcessExpectingFailure(MockEnvironment environment) {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        IllegalStateException failure;
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            try {
                failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                        () -> new ProductionConfigFailFastEnvironmentPostProcessor()
                                .postProcessEnvironment(environment, null));
            } finally {
                System.setErr(original);
            }
        }
        return new Captured(failure, buffer.toString(StandardCharsets.UTF_8));
    }

    private record Captured(IllegalStateException failure, String stderr) {
    }

    @Test
    void formatReportNeverPrintsSecretValues() {
        MockEnvironment environment = dockerEnvironment();
        strongSecrets(environment);
        environment.setProperty("app.table.qr-secret", "");

        assertThatThrownBy(() -> new ProductionConfigFailFastEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(STRONG_JWT_SECRET)
                .hasMessageNotContaining(STRONG_DB_PASSWORD);
    }
}
