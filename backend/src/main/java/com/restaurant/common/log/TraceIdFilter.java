package com.restaurant.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * traceId 注入过滤器：为每个请求建立 {@link TraceContext}，并把 traceId 写回响应头。
 *
 * <ul>
 *   <li>优先复用入站 {@code X-Trace-Id}（格式校验通过时），否则生成 16 位 traceId；</li>
 *   <li>无论成功失败都在 {@code finally} 中清理 MDC，避免线程复用污染；</li>
 *   <li>最高优先级执行，保证后续 Security 过滤器与异常处理的日志都带 traceId。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = TraceContext.resolve(request.getHeader(TraceContext.HEADER_NAME));
        TraceContext.set(traceId);
        response.setHeader(TraceContext.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
