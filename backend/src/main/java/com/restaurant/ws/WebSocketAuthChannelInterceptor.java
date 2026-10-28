package com.restaurant.ws;

import com.restaurant.common.log.TraceContext;
import com.restaurant.contract.OrderLookupPort;
import com.restaurant.contract.OrderLookupPort.OrderBrief;
import com.restaurant.security.JwtClaims;
import com.restaurant.security.JwtTokenExpiredException;
import com.restaurant.security.JwtTokenInvalidException;
import com.restaurant.security.JwtTokenProvider;
import com.restaurant.table.TableInfo;
import com.restaurant.table.mapper.TableInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 入站鉴权与订阅 ACL（修复 docs/05-security.md H-01）。
 *
 * <p>规则：
 * <ol>
 *   <li>CONNECT 必须携带有效 JWT（{@code Authorization: Bearer <jwt>}，兼容
 *       {@code authorization}/{@code token}/{@code access_token} 原生头与 {@code passcode}），
 *       校验通过后把 {@link WsPrincipal} 写入 {@code accessor.setUser} 并缓存到会话属性；</li>
 *   <li>SUBSCRIBE 默认拒绝（DENY BY DEFAULT），仅放行下列目的地：
 *     <ul>
 *       <li>{@code /topic/admin/**}：仅 ADMIN；</li>
 *       <li>{@code /topic/merchant/{merchantId}/orders|tables}：ADMIN 或 merchantId 匹配的 MERCHANT；</li>
 *       <li>{@code /topic/table/{tableId}}：ADMIN 或该桌所属 MERCHANT；</li>
 *       <li>{@code /topic/orders/{orderId}}：ADMIN、该订单所属商家或下单顾客本人；</li>
 *       <li>{@code /user/**}：任意已认证用户。</li>
 *     </ul>
 *   </li>
 *   <li>其它 SUBSCRIBE 目的地、所有 SEND（{@code /app/**}、{@code /queue/**}、{@code /topic/**}）
 *       一律拒绝；</li>
 *   <li>拒绝时抛出 {@link WsAuthException}，由 {@code StompSubProtocolHandler} 回 ERROR 帧
 *       （message 头带 {@code WS_UNAUTHORIZED}/{@code WS_FORBIDDEN} 前缀）并以
 *       {@code CloseStatus.PROTOCOL_ERROR} 关闭连接，同时记录 WARN（含 traceId/会话/用户/目的地）。</li>
 * </ol>
 *
 * <p>握手来源限制仍由 {@code WebSocketConfig#registerStompEndpoints} 的
 * {@code setAllowedOriginPatterns} 负责；HTTP 层 {@code /ws/**} 的 permitAll 只放行握手，
 * STOMP 层鉴权在本类完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    /** 会话属性：认证后的主体。 */
    static final String ATTR_PRINCIPAL = "ws.auth.principal";

    /** 会话属性：traceId（CONNECT 时确定，后续帧复用）。 */
    static final String ATTR_TRACE_ID = "ws.auth.traceId";

    /** 允许的 CONNECT 认证头（大小写不敏感地顺序探测）。 */
    private static final String[] AUTH_HEADERS = {"Authorization", "authorization", "token", "access_token"};

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Pattern ADMIN_TOPIC = Pattern.compile("^/topic/admin(?:/.*)?$");

    private static final Pattern MERCHANT_TOPIC =
            Pattern.compile("^/topic/merchant/(\\d+)/(?:orders|tables)$");

    private static final Pattern TABLE_TOPIC = Pattern.compile("^/topic/table/(\\d+)$");

    private static final Pattern ORDER_TOPIC = Pattern.compile("^/topic/orders/(\\d+)$");

    private final JwtTokenProvider jwtTokenProvider;

    private final OrderLookupPort orderLookupPort;

    private final TableInfoMapper tableInfoMapper;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        String traceId = resolveTraceId(accessor);
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> authenticate(accessor, traceId);
            case SUBSCRIBE -> authorizeSubscription(accessor, traceId);
            case SEND -> throw reject(traceId, accessor, "WS_FORBIDDEN", "客户端不允许直接 SEND 到 broker");
            default -> {
                // DISCONNECT / ACK / NACK / UNSUBSCRIBE 等控制帧放行。
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor, String traceId) {
        String token = extractToken(accessor);
        if (token == null || token.isBlank()) {
            throw reject(traceId, accessor, "WS_UNAUTHORIZED", "CONNECT 缺少 Authorization/token 头");
        }
        JwtClaims claims;
        try {
            claims = jwtTokenProvider.parse(token);
        } catch (JwtTokenExpiredException ex) {
            throw reject(traceId, accessor, "WS_UNAUTHORIZED", "token 已过期");
        } catch (JwtTokenInvalidException | IllegalArgumentException ex) {
            throw reject(traceId, accessor, "WS_UNAUTHORIZED", "token 无效");
        }
        if (claims.userId() == null || claims.role() == null || claims.role().isBlank()) {
            throw reject(traceId, accessor, "WS_UNAUTHORIZED", "token 缺少 userId/role");
        }
        WsPrincipal principal = new WsPrincipal(claims.userId(), claims.username(), claims.role(),
                claims.merchantId());
        accessor.setUser(principal);
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null) {
            attributes.put(ATTR_PRINCIPAL, principal);
            attributes.put(ATTR_TRACE_ID, traceId);
        }
        log.info("WS 认证成功 traceId={} sessionId={} userId={} role={} merchantId={}",
                traceId, accessor.getSessionId(), principal.userId(), principal.role(), principal.merchantId());
    }

    private String extractToken(StompHeaderAccessor accessor) {
        for (String header : AUTH_HEADERS) {
            String value = accessor.getFirstNativeHeader(header);
            if (value != null && !value.isBlank()) {
                return stripBearer(value.trim());
            }
        }
        // 兼容部分 STOMP 客户端用 passcode 传 token 的方式。
        String passcode = accessor.getFirstNativeHeader("passcode");
        if (passcode != null && !passcode.isBlank()) {
            return stripBearer(passcode.trim());
        }
        return null;
    }

    private static String stripBearer(String value) {
        if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return value.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor, String traceId) {
        WsPrincipal principal = resolvePrincipal(accessor);
        if (principal == null) {
            throw reject(traceId, accessor, "WS_UNAUTHORIZED", "SUBSCRIBE 前未完成 CONNECT 认证");
        }
        // 让下游（user destination 等）拿到同一主体。
        accessor.setUser(principal);

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw reject(traceId, accessor, "WS_FORBIDDEN", "SUBSCRIBE 缺少 destination");
        }
        String path = normalizeDestination(destination);
        String role = principal.role();

        if (path.equals("/user") || path.startsWith("/user/")) {
            return;
        }
        if (ADMIN_TOPIC.matcher(path).matches()) {
            requireRole(principal, "ADMIN", traceId, accessor, path);
            return;
        }
        Matcher merchantMatcher = MERCHANT_TOPIC.matcher(path);
        if (merchantMatcher.matches()) {
            if ("ADMIN".equals(role)) {
                return;
            }
            if ("MERCHANT".equals(role) && merchantMatcher.group(1).equals(String.valueOf(principal.merchantId()))) {
                return;
            }
            throw reject(traceId, accessor, "WS_FORBIDDEN", "无权订阅他店频道 " + path);
        }
        Matcher tableMatcher = TABLE_TOPIC.matcher(path);
        if (tableMatcher.matches()) {
            authorizeTable(principal, parseId(tableMatcher.group(1)), traceId, accessor, path);
            return;
        }
        Matcher orderMatcher = ORDER_TOPIC.matcher(path);
        if (orderMatcher.matches()) {
            authorizeOrder(principal, parseId(orderMatcher.group(1)), traceId, accessor, path);
            return;
        }
        // /queue/**、/app/**、/topic/** 其它未列明目的地：默认拒绝。
        throw reject(traceId, accessor, "WS_FORBIDDEN", "未授权的订阅目的地 " + path);
    }

    private void authorizeTable(WsPrincipal principal, Long tableId, String traceId,
                                StompHeaderAccessor accessor, String path) {
        if ("ADMIN".equals(principal.role())) {
            return;
        }
        if (!"MERCHANT".equals(principal.role())) {
            throw reject(traceId, accessor, "WS_FORBIDDEN", "仅管理员或桌台所属商家可订阅 " + path);
        }
        TableInfo table = tableId == null ? null : tableInfoMapper.selectById(tableId);
        if (table == null) {
            throw reject(traceId, accessor, "WS_NOT_FOUND", "桌台不存在 " + path);
        }
        if (!Objects.equals(table.getMerchantId(), principal.merchantId())) {
            throw reject(traceId, accessor, "WS_FORBIDDEN", "无权订阅他店桌台 " + path);
        }
    }

    private void authorizeOrder(WsPrincipal principal, Long orderId, String traceId,
                                StompHeaderAccessor accessor, String path) {
        if ("ADMIN".equals(principal.role())) {
            return;
        }
        OrderBrief order = orderId == null ? null : orderLookupPort.get(orderId);
        if (order == null) {
            throw reject(traceId, accessor, "WS_NOT_FOUND", "订单不存在 " + path);
        }
        if ("MERCHANT".equals(principal.role())
                && Objects.equals(order.merchantId(), principal.merchantId())) {
            return;
        }
        if ("CUSTOMER".equals(principal.role())
                && Objects.equals(order.userId(), principal.userId())) {
            return;
        }
        throw reject(traceId, accessor, "WS_FORBIDDEN", "无权订阅该订单频道 " + path);
    }

    private void requireRole(WsPrincipal principal, String requiredRole, String traceId,
                             StompHeaderAccessor accessor, String path) {
        if (!requiredRole.equals(principal.role())) {
            throw reject(traceId, accessor, "WS_FORBIDDEN", "需要 " + requiredRole + " 角色才能订阅 " + path);
        }
    }

    private WsPrincipal resolvePrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof WsPrincipal principal) {
            return principal;
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null && attributes.get(ATTR_PRINCIPAL) instanceof WsPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String resolveTraceId(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object stored = attributes == null ? null : attributes.get(ATTR_TRACE_ID);
        if (stored instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String incoming = accessor.getFirstNativeHeader(TraceContext.HEADER_NAME);
        return TraceContext.resolve(incoming);
    }

    private WsAuthException reject(String traceId, StompHeaderAccessor accessor, String code, String detail) {
        WsPrincipal principal = resolvePrincipal(accessor);
        log.warn("WS 请求被拒绝 traceId={} sessionId={} command={} destination={} userId={} role={} code={} reason={}",
                traceId, accessor.getSessionId(), accessor.getCommand(), accessor.getDestination(),
                principal == null ? null : principal.userId(), principal == null ? null : principal.role(),
                code, detail);
        return new WsAuthException(code, detail);
    }

    private static String normalizeDestination(String destination) {
        int cut = destination.length();
        int query = destination.indexOf('?');
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        int fragment = destination.indexOf('#');
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        String path = destination.substring(0, cut);
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static Long parseId(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
