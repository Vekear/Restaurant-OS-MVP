package com.restaurant.security;

/** JWT 无效（签名错误/格式错误/issuer 不匹配/缺少必要 claim，响应码 40103）。 */
public class JwtTokenInvalidException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JwtTokenInvalidException(String message) {
        super(message);
    }

    public JwtTokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
