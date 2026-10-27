package com.restaurant.security;

import com.restaurant.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析（HS256）。
 *
 * <p>claims 约定：{@code sub=userId}、{@code username}、{@code role}、{@code merchantId}、
 * {@code jti}、{@code iat}、{@code exp}、{@code iss=app.jwt.issuer}。
 * 密钥来自 {@code app.jwt.secret}（环境变量 JWT_SECRET），长度不足 32 字节时启动失败，
 * 避免线上默认弱密钥静默生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final AppProperties appProperties;

    private SecretKey secretKey;

    private String issuer;

    private long expireSeconds;

    @PostConstruct
    void init() {
        AppProperties.Jwt jwt = appProperties.getJwt();
        String secret = jwt.getSecret();
        if (secret == null || secret.length() < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret 长度必须 >= 32 字节，请通过环境变量 JWT_SECRET 注入强随机密钥");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = jwt.getIssuer();
        this.expireSeconds = Math.max(1L, jwt.getExpireMinutes()) * 60L;
        log.info("JWT 初始化完成 issuer={} expireSeconds={}", issuer, expireSeconds);
    }

    /** 签发 access token。 */
    public IssuedToken issue(Long userId, String username, String role, Long merchantId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expireSeconds);
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("merchantId", merchantId)
                .id(jti)
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, jti, expiresAt, expireSeconds);
    }

    /**
     * 解析并校验 token。
     *
     * @throws JwtTokenExpiredException token 过期
     * @throws JwtTokenInvalidException 签名/格式/issuer/必要 claim 非法
     */
    public JwtClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = parseUserId(claims.getSubject());
            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                throw new JwtTokenInvalidException("JWT 缺少 role claim");
            }
            Number merchantIdClaim = claims.get("merchantId", Number.class);
            Long merchantId = merchantIdClaim == null ? null : merchantIdClaim.longValue();
            return new JwtClaims(
                    userId,
                    claims.get("username", String.class),
                    role,
                    merchantId,
                    claims.getId(),
                    claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        } catch (ExpiredJwtException exception) {
            throw new JwtTokenExpiredException("JWT 已过期");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtTokenInvalidException("JWT 校验失败", exception);
        }
    }

    /** 过期秒数（用于 AuthTokenView.expiresIn）。 */
    public long expireSeconds() {
        return expireSeconds;
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new JwtTokenInvalidException("JWT sub 非法", exception);
        }
    }

    /**
     * 签发结果。
     *
     * @param token      JWT 字符串
     * @param jti        token 唯一标识
     * @param expiresAt  过期时刻
     * @param expiresIn  有效秒数
     */
    public record IssuedToken(String token, String jti, Instant expiresAt, long expiresIn) {
    }
}
