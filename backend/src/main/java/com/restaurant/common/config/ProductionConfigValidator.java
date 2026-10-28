package com.restaurant.common.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 生产配置安全校验（docs/05-security.md M-01 / M-02 的启动期防线）。
 *
 * <p>规则：
 * <ul>
 *   <li>非 {@code local}/{@code test} profile：JWT / 支付回调 / 桌台二维码密钥以及数据库密码
 *       不得为空、不得仍是 {@code application.yml} 的开发默认值或占位值；JWT 密钥至少 32 字节。</li>
 *   <li>{@code app.storage.type=oss}（任意 profile）：{@code app.oss.*} 五个必填项必须齐全，
 *       避免生产误用本地存储或运行期才暴露配置缺失。</li>
 * </ul>
 *
 * <p>本类只负责判定与提示文本，<b>不读取也不输出任何密钥值</b>；错误信息仅包含属性名、
 * 环境变量名与配置建议，保证日志/CI 输出不会泄露秘密。
 */
public final class ProductionConfigValidator {

    /** 本地开发 profile：允许 application.yml 中的开发默认值。 */
    public static final String PROFILE_LOCAL = "local";
    /** 自动化测试 profile：允许开发默认值（测试库/测试密钥均非生产数据）。 */
    public static final String PROFILE_TEST = "test";

    /** JWT 密钥最小字节数（HS256 建议 >= 32 字节，即 256 bit）。 */
    public static final int JWT_MIN_BYTES = 32;

    // 与 application.yml 中各 ${ENV:default} 的开发默认值保持一致；
    // ProductionConfigFailFastTest#developmentDefaultsStayInSyncWithApplicationYml 负责守护同步。
    static final String DEV_DEFAULT_JWT_SECRET = "restaurant-mvp-dev-only-jwt-secret-please-change-0123456789";
    static final String DEV_DEFAULT_PAY_CALLBACK_SECRET = "restaurant-mvp-dev-only-pay-secret-0123456789";
    static final String DEV_DEFAULT_TABLE_QR_SECRET = "restaurant-mvp-dev-only-table-qr-secret-0123456789";
    static final String DEV_DEFAULT_DB_PASSWORD = "123456";

    /** 占位值特征（大小写不敏感）：出现即视为“仍需替换”的开发/示例密钥。 */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)(dev[-_ ]?only|local[-_ ]?only|change[-_ ]?me|please[-_ ]?change|placeholder|replace[-_ ]?me"
                    + "|your[-_ ]?(secret|password|key)|example[-_ ]?(secret|key|password)"
                    + "|test[-_ ]?only|dummy)");

    /** 已知演示/默认数据库口令（application.yml 与 deploy/docker-compose.yml 的历史默认值）。 */
    private static final Set<String> KNOWN_DEMO_DB_PASSWORDS = Set.of(
            DEV_DEFAULT_DB_PASSWORD, "root123456");

    private static final List<SecretRule> SECRET_RULES = List.of(
            new SecretRule("app.jwt.secret", "JWT_SECRET", DEV_DEFAULT_JWT_SECRET, JWT_MIN_BYTES),
            new SecretRule("app.payment.callback-secret", "PAYMENT_CALLBACK_SECRET",
                    DEV_DEFAULT_PAY_CALLBACK_SECRET, 0),
            new SecretRule("app.table.qr-secret", "TABLE_QR_SECRET", DEV_DEFAULT_TABLE_QR_SECRET, 0),
            new SecretRule("spring.datasource.password", "DB_PASSWORD", DEV_DEFAULT_DB_PASSWORD, 0));

    private static final List<OssRule> OSS_RULES = List.of(
            new OssRule("app.oss.endpoint", "OSS_ENDPOINT"),
            new OssRule("app.oss.bucket", "OSS_BUCKET"),
            new OssRule("app.oss.access-key-id", "OSS_ACCESS_KEY_ID"),
            new OssRule("app.oss.access-key-secret", "OSS_ACCESS_KEY_SECRET"),
            new OssRule("app.oss.public-base-url", "OSS_PUBLIC_BASE_URL"));

    private ProductionConfigValidator() {
    }

    /**
     * 校验当前环境；返回值只包含“哪些属性不合规”，不包含任何属性值。
     */
    public static Validation validate(Environment environment) {
        List<String> profiles = effectiveProfiles(environment);
        List<Violation> violations = new ArrayList<>();
        if (!isLocalOrTest(profiles)) {
            validateSecrets(environment, violations);
        }
        validateOss(environment, violations);
        return new Validation(List.copyOf(profiles), List.copyOf(violations));
    }

    /** 生成 stderr 报告：仅含属性名 / 环境变量名 / 配置提示，绝不含密钥值。 */
    public static String formatReport(Validation validation) {
        StringBuilder report = new StringBuilder(512);
        report.append("[FATAL] 生产配置校验失败（production config fail-fast）：拒绝启动，共 ")
                .append(validation.violations().size()).append(" 项问题\n");
        report.append("[FATAL] effective profiles: ")
                .append(validation.profiles().isEmpty() ? "<none>" : String.join(",", validation.profiles()))
                .append('\n');
        for (Violation violation : validation.violations()) {
            report.append("[FATAL]   - ").append(violation.property()).append("：")
                    .append(violation.detail()).append('\n');
        }
        report.append("[FATAL] 提示：以上仅为属性名与配置项说明，不包含任何密钥值。"
                + "非 local/test profile 请通过环境变量注入真实密钥；OSS 模式请补全 app.oss.*。"
                + "参考 deploy/.env.example 与 docs/06-deployment.md。\n");
        return report.toString();
    }

    private static void validateSecrets(Environment environment, List<Violation> violations) {
        for (SecretRule rule : SECRET_RULES) {
            String value = environment.getProperty(rule.property());
            String hint = "非 local/test profile 必须通过环境变量 " + rule.envVar()
                    + " 注入仅本环境使用的真实值，禁止使用开发默认值/占位值";
            if (!StringUtils.hasText(value)) {
                violations.add(new Violation(rule.property(), "未配置（空值）；" + hint));
                continue;
            }
            String candidate = value.trim();
            if (isDevelopmentValue(rule, candidate)) {
                violations.add(new Violation(rule.property(),
                        "仍为开发默认值/占位值（application.yml 默认值，或含 dev-only / please-change 等占位标记）；" + hint));
                continue;
            }
            if (rule.minBytes() > 0) {
                int bytes = candidate.getBytes(StandardCharsets.UTF_8).length;
                if (bytes < rule.minBytes()) {
                    violations.add(new Violation(rule.property(),
                            "长度不足 " + rule.minBytes() + " 字节（当前 " + bytes + " 字节），"
                                    + "HS256 签名密钥需 >= 32 字节随机值；" + hint));
                }
            }
        }
    }

    private static boolean isDevelopmentValue(SecretRule rule, String candidate) {
        if (candidate.equals(rule.devDefault())) {
            return true;
        }
        if ("spring.datasource.password".equals(rule.property())
                && KNOWN_DEMO_DB_PASSWORDS.contains(candidate)) {
            return true;
        }
        return PLACEHOLDER_PATTERN.matcher(candidate).find();
    }

    private static void validateOss(Environment environment, List<Violation> violations) {
        String storageType = environment.getProperty("app.storage.type");
        if (storageType == null || !"oss".equals(storageType.trim().toLowerCase(Locale.ROOT))) {
            return;
        }
        for (OssRule rule : OSS_RULES) {
            if (!StringUtils.hasText(environment.getProperty(rule.property()))) {
                violations.add(new Violation(rule.property(),
                        "app.storage.type=oss 时必须配置（环境变量 " + rule.envVar()
                                + "），否则 OSS 存储不可用；如无需 OSS 请显式设置 STORAGE_TYPE=local"));
            }
        }
    }

    /**
     * 解析“生效 profile”：有显式 active 时以 active 为准；否则回退 spring.profiles.default（本项目默认 local）。
     *
     * <p>不使用 {@link Environment#getDefaultProfiles()} 的兜底，避免 docker 启动时把 default 误判为 local。
     */
    static List<String> effectiveProfiles(Environment environment) {
        Set<String> profiles = new LinkedHashSet<>();
        for (String profile : environment.getActiveProfiles()) {
            if (StringUtils.hasText(profile)) {
                profiles.add(profile.trim());
            }
        }
        if (profiles.isEmpty()) {
            String configuredDefault = environment.getProperty("spring.profiles.default");
            if (StringUtils.hasText(configuredDefault)) {
                for (String profile : configuredDefault.split(",")) {
                    if (StringUtils.hasText(profile)) {
                        profiles.add(profile.trim());
                    }
                }
            }
        }
        if (profiles.isEmpty()) {
            for (String profile : environment.getDefaultProfiles()) {
                if (StringUtils.hasText(profile)) {
                    profiles.add(profile.trim());
                }
            }
        }
        return List.copyOf(profiles);
    }

    private static boolean isLocalOrTest(List<String> profiles) {
        return profiles.stream().anyMatch(profile -> PROFILE_LOCAL.equals(profile) || PROFILE_TEST.equals(profile));
    }

    /** 单个秘密项规则。 */
    private record SecretRule(String property, String envVar, String devDefault, int minBytes) {
    }

    /** 单个 OSS 必填项。 */
    private record OssRule(String property, String envVar) {
    }

    /** 一条不合规记录：属性名 + 配置建议（不含属性值）。 */
    public record Violation(String property, String detail) {
    }

    /** 校验结论。 */
    public record Validation(List<String> profiles, List<Violation> violations) {

        public boolean failed() {
            return !violations.isEmpty();
        }

        /** 仅属性名，用于异常信息（不泄露值）。 */
        public String failedProperties() {
            return violations.stream().map(Violation::property).collect(Collectors.joining(", "));
        }
    }
}
