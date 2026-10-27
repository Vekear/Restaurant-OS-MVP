package com.restaurant.security;

/** JWT 已过期（响应码 40102）。 */
public class JwtTokenExpiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JwtTokenExpiredException(String message) {
        super(message);
    }
}
