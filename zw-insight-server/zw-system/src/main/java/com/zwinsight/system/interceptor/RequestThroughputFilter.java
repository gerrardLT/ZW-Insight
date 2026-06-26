package com.zwinsight.system.interceptor;

import io.micrometer.core.instrument.Counter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求吞吐量统计过滤器。
 *
 * <p>对每个进入的 HTTP 请求递增 Micrometer Counter {@code app.requests.total}，
 * 为监控仪表盘提供真实的请求吞吐量指标。
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class RequestThroughputFilter extends OncePerRequestFilter {

    private final Counter requestThroughputCounter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            requestThroughputCounter.increment();
        } catch (Exception ignored) {
            // 指标统计失败不应影响正常请求处理
        }
        filterChain.doFilter(request, response);
    }
}
