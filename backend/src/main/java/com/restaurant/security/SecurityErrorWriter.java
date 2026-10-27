package com.restaurant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.api.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 在过滤器/失败处理器中输出统一 JSON 响应体（与 Controller 返回格式一致）。
 *
 * <p>此时 TraceIdFilter 已设置 MDC，{@link Result} 的 traceId 会自动带上链路 ID。
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    /** 输出 JSON 响应（无额外响应头）。 */
    public void write(HttpServletResponse response, int httpStatus, Result<?> body) throws IOException {
        write(response, httpStatus, body, Map.of());
    }

    /** 输出 JSON 响应并附加响应头（例如 429 的 Retry-After）。 */
    public void write(HttpServletResponse response, int httpStatus, Result<?> body, Map<String, String> headers)
            throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        headers.forEach(response::setHeader);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
