package com.restaurant.security;

/**
 * Security 过滤器之间的请求属性约定。
 *
 * <p>JWT 解析失败时把具体错误码放入 request attribute，由
 * {@link RestAuthenticationEntryPoint} 在进入受保护资源时输出对应 401 业务码
 * （40101 未认证 / 40102 已过期 / 40103 无效）。
 */
public final class SecurityAttributes {

    /** 值为 {@link com.restaurant.common.api.ErrorCode}。 */
    public static final String ATTR_ERROR_CODE = SecurityAttributes.class.getName() + ".ERROR_CODE";

    private SecurityAttributes() {
    }
}
