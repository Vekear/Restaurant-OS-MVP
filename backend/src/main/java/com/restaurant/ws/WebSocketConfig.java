package com.restaurant.ws;

import com.restaurant.common.config.AppProperties;
import com.restaurant.common.constant.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 配置（{@code docs/01-architecture.md §12}）。
 *
 * <ul>
 *   <li>端点：{@code /ws}，纯 WebSocket（明确关闭 SockJS 回退）；</li>
 *   <li>broker：简单内存 broker，订阅前缀 {@code /topic}（订单/商家桌台/后台频道）、{@code /queue}；</li>
 *   <li>应用前缀 {@code /app}，用户前缀 {@code /user}；</li>
 *   <li>允许来源复用 app.cors 配置（浏览器握手校验，非浏览器客户端不受 Origin 限制）。</li>
 * </ul>
 *
 * <p>T08 将补充：握手 query token 鉴权 ChannelInterceptor、订阅 ACL、心跳
 * （{@code setHeartbeatValue(10000,10000)}）与原生 {@code /ws/native} 通道。
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties appProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ApiConstants.WS_ENDPOINT)
                .setAllowedOriginPatterns(appProperties.getCors().getAllowedOriginPatterns().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(ApiConstants.WS_TOPIC_PREFIX, ApiConstants.WS_QUEUE_PREFIX);
        registry.setApplicationDestinationPrefixes(ApiConstants.WS_APPLICATION_PREFIX);
        registry.setUserDestinationPrefix(ApiConstants.WS_USER_PREFIX);
    }
}
