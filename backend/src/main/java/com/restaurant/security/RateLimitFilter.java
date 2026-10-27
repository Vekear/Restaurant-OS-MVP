package com.restaurant.security;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.Result;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.util.CacheKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * 已认证请求的 Redis 限流过滤器（{@code docs/01-architecture.md §16.8}）。
 *
 * <p>阈值：下单 10 次/分/用户、支付 10 次/分/用户、其余 API 120 次/分/用户；
 * 超限返回 HTTP 429 + 业务码 42901 + {@code Retry-After}。
 * 未认证请求（登录/注册/公开菜单）由 Service 层按 IP 单独限流，本过滤器不处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimitService rateLimitService;

    private final AppProperties appProperties;

    private final SecurityErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            filterChain.doFilter(request, response);
            return;
        }
        AppProperties.RateLimit rateLimit = appProperties.getRateLimit();
        String method = request.getMethod();
        int limit;
        String key;
        if (isOrderCreate(method, path)) {
            limit = rateLimit.getOrder();
            key = CacheKeys.rateOrderKey(principal.getId());
        } else if (path.startsWith(API_PREFIX + "payments")) {
            limit = rateLimit.getPayment();
            key = CacheKeys.ratePayKey(principal.getId());
        } else {
            limit = rateLimit.getApi();
            key = CacheKeys.rateApiKey(principal.getId(), method, path);
        }
        RateLimitResult result = rateLimitService.check(key, limit, WINDOW);
        if (!result.allowed()) {
            log.warn("请求被限流 userId={} method={} path={} retryAfter={}s",
                    principal.getId(), method, path, result.retryAfterSeconds());
            errorWriter.write(response, ErrorCode.TOO_MANY_REQUESTS.getStatus().value(),
                    Result.failure(ErrorCode.TOO_MANY_REQUESTS),
                    Map.of(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds())));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isOrderCreate(String method, String path) {
        if (!"POST".equals(method)) {
            return false;
        }
        return (API_PREFIX + "orders").equals(path) || (API_PREFIX + "dine-in/orders").equals(path);
    }

    private UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof UserPrincipal principal ? principal : null;
    }
}
