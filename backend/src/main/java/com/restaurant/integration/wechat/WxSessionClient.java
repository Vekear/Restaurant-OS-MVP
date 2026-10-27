package com.restaurant.integration.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 微信 code2session 客户端（{@code docs/02-api.md §4.3}、ADR-0010）。
 *
 * <p>mock 模式（默认）：不发起任何 HTTP 请求，{@code openid = "mock_" + SHA-256(code)[0:16]}，
 * 同一 code 稳定映射，便于自动化测试；此时不生成 session_key。
 * 真实模式：调用微信 jscode2session；AppID/AppSecret 缺失时启动 WARN 并拒绝登录（40101），
 * 不做静默降级。网络失败、微信返回 errcode 一律对前端表现为凭证无效，细节仅记 WARN。
 */
@Slf4j
@Component
public class WxSessionClient {

    /** 与 docs/02-api.md §4.3 保持一致（ADR-0010 写的是 24 位，以 API 契约为准）。 */
    private static final int MOCK_OPENID_HASH_LENGTH = 16;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final AppProperties appProperties;

    private final ObjectMapper objectMapper;

    private final RestClient restClient;

    public WxSessionClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @PostConstruct
    void checkConfiguration() {
        AppProperties.Wechat wechat = appProperties.getWechat();
        if (!wechat.isMock() && (!StringUtils.hasText(wechat.getAppId()) || !StringUtils.hasText(wechat.getAppSecret()))) {
            log.error("微信登录为真实模式但缺少 app.wechat.app-id/app-secret，小程序登录将一律失败（40101）");
        }
    }

    /**
     * 用临时 code 换取微信用户标识。
     *
     * @throws BizException 40101 凭证无效（不暴露微信错误细节）
     */
    public WxSession code2Session(String code) {
        AppProperties.Wechat wechat = appProperties.getWechat();
        if (wechat.isMock()) {
            String openid = "mock_" + sha256Hex(code).substring(0, MOCK_OPENID_HASH_LENGTH);
            log.debug("微信 mock 登录 openid={}", openid);
            return new WxSession(openid, null);
        }
        if (!StringUtils.hasText(wechat.getAppId()) || !StringUtils.hasText(wechat.getAppSecret())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "微信登录未配置");
        }
        try {
            String body = restClient.get()
                    .uri(wechat.getCode2sessionUrl()
                                    + "?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
                            wechat.getAppId(), wechat.getAppSecret(), code)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body);
            String openid = node.path("openid").isMissingNode() ? null : node.path("openid").asText(null);
            if (!StringUtils.hasText(openid)) {
                log.warn("微信 code2session 失败 errcode={} errmsg={}",
                        node.path("errcode").asInt(), node.path("errmsg").asText(""));
                throw new BizException(ErrorCode.UNAUTHENTICATED);
            }
            String unionid = node.path("unionid").isMissingNode() ? null : node.path("unionid").asText(null);
            return new WxSession(openid, StringUtils.hasText(unionid) ? unionid : null);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("微信 code2session 调用异常: {}", exception.getMessage());
            throw new BizException(ErrorCode.UNAUTHENTICATED, "微信登录失败，请重试");
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
