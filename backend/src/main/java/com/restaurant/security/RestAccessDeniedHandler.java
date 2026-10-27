package com.restaurant.security;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已认证但角色/资源权限不足：输出 403 + 40301。
 *
 * <p>注意区分 40302（资源归属不符，由业务层抛 BizException）与 40101（未认证）。
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(response, ErrorCode.ROLE_FORBIDDEN.getStatus().value(),
                Result.failure(ErrorCode.ROLE_FORBIDDEN));
    }
}
