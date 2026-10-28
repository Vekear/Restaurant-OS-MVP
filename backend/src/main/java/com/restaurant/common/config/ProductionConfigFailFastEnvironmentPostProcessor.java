package com.restaurant.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.charset.StandardCharsets;

/**
 * 生产配置 fail-fast（docs/05-security.md M-01）。
 *
 * <p>通过 {@code META-INF/spring.factories} 注册为 {@link EnvironmentPostProcessor}，
 * 在 Environment 准备完成、任何 Bean（DataSource / Flyway / MyBatis 等）初始化之前执行：
 * <ul>
 *   <li>非 local/test profile 且密钥缺失/仍为开发默认值/长度不足 → 向 stderr 打印
 *       <b>不含密钥值</b>的 {@code [FATAL]} 报告后抛出异常，进程以非 0 退出；</li>
 *   <li>{@code app.storage.type=oss} 且 {@code app.oss.*} 必填项缺失 → 同样拒绝启动。</li>
 * </ul>
 *
 * <p>由于此时数据库连接尚未建立，失败不会产生半初始化的数据（Flyway 不会执行迁移）。
 * 报告直接写 stderr（不经过日志框架），保证在日志系统完全就绪前也可被启动脚本/CI 捕获。
 */
public class ProductionConfigFailFastEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 报告前缀，供启动脚本/CI 断言使用。 */
    public static final String REPORT_PREFIX = "[FATAL]";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ProductionConfigValidator.Validation validation = ProductionConfigValidator.validate(environment);
        if (!validation.failed()) {
            return;
        }
        // 直接以 UTF-8 字节写入 stderr：容器/CI 日志统一按 UTF-8 采集，
        // 避免 Windows 控制台 GBK 与 UTF-8 混用导致中文提示乱码（ASCII 部分始终可读）。
        byte[] report = ProductionConfigValidator.formatReport(validation).getBytes(StandardCharsets.UTF_8);
        System.err.write(report, 0, report.length);
        System.err.flush();
        throw new IllegalStateException("生产配置校验失败（profiles=" + validation.profiles() + "）："
                + validation.failedProperties()
                + " 未配置、仍为开发默认值或长度不足；详见 stderr 的 " + REPORT_PREFIX + " 报告");
    }

    /**
     * 晚于 ConfigData（application*.yml / 环境变量）加载，早于所有 Bean 初始化。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
