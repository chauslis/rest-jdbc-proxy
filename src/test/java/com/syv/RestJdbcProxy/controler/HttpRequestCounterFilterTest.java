package com.syv.RestJdbcProxy.controler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestCounterFilterTest {

    @Test
    void countsRequestUsingFirstTwoPathSegments() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HttpRequestCounterFilter filter = new HttpRequestCounterFilter(meterRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/batch/test/extra");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);
        FilterChain chain = (servletRequest, servletResponse) -> { };

        filter.doFilter(request, response, chain);

        Counter counter = meterRegistry.find("http.requests.batch.test.count")
                .tag("method", "POST")
                .tag("uri", "batch.test")
                .tag("status", "201")
                .counter();
        assertEquals(1.0, counter.count());
    }

    @Test
    void countsBlankUriAsUnknown() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HttpRequestCounterFilter filter = new HttpRequestCounterFilter(meterRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        Counter counter = meterRegistry.find("http.requests.unknown.count")
                .tag("uri", "unknown")
                .counter();
        assertEquals(1.0, counter.count());
    }
}
