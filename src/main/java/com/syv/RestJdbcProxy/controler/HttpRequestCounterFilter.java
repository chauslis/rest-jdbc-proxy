package com.syv.RestJdbcProxy.controler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class HttpRequestCounterFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    public HttpRequestCounterFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        filterChain.doFilter(request, response);
        String url = firstTwoSegmentsFast(request.getRequestURI());
        Tags tags = Tags.of(
                "method", request.getMethod(),
                "uri", url,
                "status", String.valueOf(response.getStatus())
        );

        meterRegistry.counter("http.requests."+ url +".count", tags).increment();
    }

    private String firstTwoSegmentsFast(String uri) {
        if (uri == null || uri.isBlank()) {
            return "unknown";
        }

        String trimmed = uri.startsWith("/") ? uri.substring(1) : uri;

        int firstSlash = trimmed.indexOf('/');
        if (firstSlash < 0) {
            return trimmed;
        }

        int secondSlash = trimmed.indexOf('/', firstSlash + 1);
        if (secondSlash < 0) {
            return trimmed.replace('/','.');
        }

        return trimmed.substring(0, secondSlash).replace('/','.');
    }
}
