package com.restaurant.security;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证入口：输出 401 + 业务码。
 *
 * <p>JwtAuthenticationFilter 已解析出具体原因时使用请求属性中的错误码
 * （40102 过期 / 40103 无效），否则为 40101 未认证。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attribute = request.getAttribute(SecurityAttributes.ATTR_ERROR_CODE);
        ErrorCode errorCode = attribute instanceof ErrorCode code ? code : ErrorCode.UNAUTHENTICATED;
        errorWriter.write(response, errorCode.getStatus().value(), Result.failure(errorCode));
    }
}
