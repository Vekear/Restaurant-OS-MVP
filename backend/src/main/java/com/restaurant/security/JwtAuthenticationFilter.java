package com.restaurant.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.Result;
import com.restaurant.common.enums.UserStatus;
import com.restaurant.common.util.CacheKeys;
import com.restaurant.merchant.Merchant;
import com.restaurant.merchant.mapper.MerchantMapper;
import com.restaurant.user.User;
import com.restaurant.user.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT 认证过滤器：解析 {@code Authorization: Bearer <token>} 并建立 SecurityContext。
 *
 * <p>处理顺序：签名/过期校验 → 登出黑名单（Redis jwt:blacklist:{jti}）→ 用户存在性与状态校验
 * → （MERCHANT）店铺 ID 解析 → 写入 SecurityContext。
 * 失败时不直接返回，而是把错误码放入请求属性，交给 Spring Security 的
 * {@link RestAuthenticationEntryPoint} 在受保护资源处输出 401（40102/40103/40101）；
 * 账号被禁用则立即输出 403 + 40304。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final long REDIS_WARN_INTERVAL_MILLIS = 60_000L;

    private final JwtTokenProvider jwtTokenProvider;

    private final UserMapper userMapper;

    private final MerchantMapper merchantMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final SecurityErrorWriter errorWriter;

    private final AtomicLong lastRedisWarnAt = new AtomicLong(0L);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }
        JwtClaims claims;
        try {
            claims = jwtTokenProvider.parse(token);
        } catch (JwtTokenExpiredException exception) {
            request.setAttribute(SecurityAttributes.ATTR_ERROR_CODE, ErrorCode.TOKEN_EXPIRED);
            filterChain.doFilter(request, response);
            return;
        } catch (JwtTokenInvalidException exception) {
            request.setAttribute(SecurityAttributes.ATTR_ERROR_CODE, ErrorCode.TOKEN_INVALID);
            filterChain.doFilter(request, response);
            return;
        }
        if (isRevoked(claims.jti())) {
            request.setAttribute(SecurityAttributes.ATTR_ERROR_CODE, ErrorCode.TOKEN_INVALID);
            filterChain.doFilter(request, response);
            return;
        }
        User user;
        try {
            user = userMapper.selectById(claims.userId());
        } catch (RuntimeException exception) {
            log.error("根据 JWT 加载用户失败 userId={}", claims.userId(), exception);
            filterChain.doFilter(request, response);
            return;
        }
        if (user == null) {
            request.setAttribute(SecurityAttributes.ATTR_ERROR_CODE, ErrorCode.TOKEN_INVALID);
            filterChain.doFilter(request, response);
            return;
        }
        if (!UserStatus.isEnabled(user.getStatus())) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, ErrorCode.ACCOUNT_DISABLED.getStatus().value(),
                    Result.failure(ErrorCode.ACCOUNT_DISABLED));
            return;
        }
        Long merchantId = resolveMerchantId(user, claims.merchantId());
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), merchantId, user.getStatus(), claims.jti(), claims.expiresAt());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean isRevoked(String jti) {
        if (!StringUtils.hasText(jti)) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(CacheKeys.jwtBlacklistKey(jti)));
        } catch (RuntimeException exception) {
            warnRedisUnavailable(exception);
            // Redis 故障时无法判定黑名单，按未撤销处理（fail-open），保证登录态可用性
            return false;
        }
    }

    private Long resolveMerchantId(User user, Long merchantIdFromToken) {
        if (!"MERCHANT".equals(user.getRole())) {
            return null;
        }
        if (merchantIdFromToken != null) {
            return merchantIdFromToken;
        }
        try {
            Merchant merchant = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                    .eq(Merchant::getUserId, user.getId())
                    .last("LIMIT 1"));
            return merchant == null ? null : merchant.getId();
        } catch (RuntimeException exception) {
            log.warn("解析商家店铺失败 userId={}: {}", user.getId(), exception.getMessage());
            return null;
        }
    }

    private void warnRedisUnavailable(RuntimeException exception) {
        long now = System.currentTimeMillis();
        long previous = lastRedisWarnAt.get();
        if (now - previous < REDIS_WARN_INTERVAL_MILLIS || !lastRedisWarnAt.compareAndSet(previous, now)) {
            return;
        }
        log.warn("Redis 不可用，JWT 黑名单校验降级放行: {}", exception.getMessage());
    }
}
