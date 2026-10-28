package com.restaurant.ws;

import org.springframework.messaging.MessagingException;

/**
 * STOMP 鉴权/订阅 ACL 拒绝异常。
 *
 * <p>由入站 {@code ChannelInterceptor#preSend} 抛出，{@code StompSubProtocolHandler}
 * 捕获后向客户端回 ERROR 帧（{@code message} 头带本异常消息）并以
 * {@code CloseStatus.PROTOCOL_ERROR} 关闭底层 WebSocket 连接。
 */
public class WsAuthException extends MessagingException {

    private final String code;

    public WsAuthException(String code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
