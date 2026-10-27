package com.restaurant.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务配置（前缀 {@code app}），键名与 {@code docs/01-architecture.md §16.9} 对齐。
 *
 * <p>{@code application.yml} 给出本地开发默认值，容器/生产通过环境变量覆盖（deploy/.env）；
 * 所有密钥类配置在 yml 中只允许出现开发占位值。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 应用版本，仅用于 OpenAPI / 系统接口展示。 */
    private String version = "0.1.0-SNAPSHOT";

    /** 默认商家 ID（MVP 单商家；公开菜单缺省取该店铺）。 */
    private Long defaultMerchantId = 1L;

    private final Cors cors = new Cors();

    private final Jwt jwt = new Jwt();

    private final Storage storage = new Storage();

    private final Upload upload = new Upload();

    private final Payment payment = new Payment();

    private final Order order = new Order();

    private final Idempotency idempotency = new Idempotency();

    private final Table table = new Table();

    private final Wechat wechat = new Wechat();

    private final Cache cache = new Cache();

    private final RateLimit rateLimit = new RateLimit();

    private final Stock stock = new Stock();

    private final Task task = new Task();

    /** 跨域配置（仅开发环境放行 Vite dev server）。 */
    @Data
    public static class Cors {

        /** 允许的来源，支持 Spring origin pattern 语法。 */
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"
        ));

        private long maxAgeSeconds = 3600L;
    }

    /** JWT HS256 配置。 */
    @Data
    public static class Jwt {

        /** HS256 密钥，长度必须 >= 32 字节；生产通过 JWT_SECRET 注入。 */
        private String secret = "";

        private long expireMinutes = 120L;

        private String issuer = "restaurant-mvp";
    }

    /** 文件存储配置：{@code local | oss}，OSS 关键项缺失时降级 local。 */
    @Data
    public static class Storage {

        private String type = "local";

        private final Local local = new Local();

        private final Oss oss = new Oss();

        /** 本地磁盘兜底存储。 */
        @Data
        public static class Local {

            /** 本地根目录，运行时相对于工作目录（已加入 .gitignore）。 */
            private String root = ".runtime/storage/files";

            /** 对外访问前缀（由 WebMvcConfig 映射到本地目录）。 */
            private String urlPrefix = "/files";
        }

        /** 阿里云 OSS Post Policy 直传配置（AccessKeySecret 永不下发前端）。 */
        @Data
        public static class Oss {

            private String endpoint = "";

            private String bucket = "";

            private String accessKeyId = "";

            private String accessKeySecret = "";

            /** 对象 key 前缀。 */
            private String dir = "biz/";

            /** Post Policy 有效期（秒）。 */
            private long expireSeconds = 300L;

            /** OSS 公网访问前缀，留空则按 endpoint/bucket 推导。 */
            private String publicBaseUrl = "";
        }
    }

    /** 上传约束（类型白名单 + 大小上限）。 */
    @Data
    public static class Upload {

        private DataSize maxSize = DataSize.ofMegabytes(5);

        private List<String> allowedContentTypes = new ArrayList<>(List.of(
                "image/jpeg", "image/png", "image/webp"
        ));
    }

    /** 模拟支付配置。 */
    @Data
    public static class Payment {

        /** 模拟支付实际扣款金额（默认 0.01 元），不得大于订单应付金额。 */
        private BigDecimal mockChargeAmount = new BigDecimal("0.01");

        /** 模拟回调 HMAC 密钥。 */
        private String callbackSecret = "";

        /** 回调时间戳允许的时钟偏移（秒）。 */
        private long callbackAllowedClockSkewSeconds = 300L;

        /** 模拟支付网关回调的本服务基地址。 */
        private String mockCallbackBaseUrl = "http://127.0.0.1:8080";
    }

    /** 订单配置。 */
    @Data
    public static class Order {

        /** 待支付订单超时分钟数。 */
        private int paymentTimeoutMinutes = 15;

        /** 超时扫描 cron（默认每 30 秒，对应 T08 验收）。 */
        private String autoCancelCron = "0/30 * * * * ?";

        /** 单次扫描最多处理的订单数。 */
        private int autoCancelBatchSize = 100;
    }

    /** 幂等配置。 */
    @Data
    public static class Idempotency {

        /** 幂等记录与 Redis 请求哈希的保留小时数。 */
        private int ttlHours = 24;
    }

    /** 桌台与会话配置。 */
    @Data
    public static class Table {

        /** 桌台二维码 HMAC-SHA256 签名密钥，生产通过 TABLE_QR_SECRET 注入。 */
        private String qrSecret = "";

        /** 会话有效期（分钟）。 */
        private int sessionExpireMinutes = 120;

        /** 会话清理 cron（默认每 5 分钟）。 */
        private String sessionCleanupCron = "0 */5 * * * ?";
    }

    /** 微信小程序登录配置。 */
    @Data
    public static class Wechat {

        /** 无真实 AppID/Secret 时开启 mock（不访问微信接口，session_key 不下发）。 */
        private boolean mock = true;

        private String appId = "";

        private String appSecret = "";

        private String code2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    }

    /** 缓存配置。 */
    @Data
    public static class Cache {

        /** 菜单类缓存 TTL（分钟），对应 docs/01-architecture.md §13。 */
        private int menuTtlMinutes = 10;
    }

    /** 限流阈值（Redis 固定窗口，每分钟），键名对齐 {@code docs/01-architecture.md §16.9}。 */
    @Data
    public static class RateLimit {

        /** 登录：按客户端 IP 计。 */
        private int login = 5;

        /** 创建订单：按用户计。 */
        private int order = 10;

        /** 支付/模拟支付：按用户计。 */
        private int payment = 10;

        /** 其余已认证接口：按用户 + 方法 + 路径计。 */
        private int api = 120;
    }

    /** 定时任务开关（仅用于测试环境关闭后台任务）。 */
    @Data
    public static class Task {

        private boolean enabled = true;
    }

    /** 库存配置。 */
    @Data
    public static class Stock {

        /** 低库存预警阈值（stock <= threshold 视为低库存）。 */
        private int warnThreshold = 5;
    }
}
