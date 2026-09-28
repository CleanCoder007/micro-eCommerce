package com.ecommerce.common.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TraceIdFilterTest {

    private RequestResponseLoggingFilter filter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new RequestResponseLoggingFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void testTraceIdGenerationWhenNotPresent() throws ServletException, IOException {
        request.setRequestURI("/api/test");
        request.setMethod("GET");

        filter.doFilter(request, response, filterChain);

        String traceId = response.getHeader("X-Trace-ID");
        assertNotNull(traceId);
        assertFalse(traceId.isEmpty());
    }

    @Test
    void testTraceIdPreservationWhenPresent() throws ServletException, IOException {
        String expectedTraceId = UUID.randomUUID().toString();
        request.addHeader("X-Trace-ID", expectedTraceId);
        request.setRequestURI("/api/test");
        request.setMethod("GET");

        filter.doFilter(request, response, filterChain);

        String traceId = response.getHeader("X-Trace-ID");
        assertEquals(expectedTraceId, traceId);
    }

    @Test
    void testMDCContainsTraceId() throws ServletException, IOException {
        String expectedTraceId = UUID.randomUUID().toString();
        request.addHeader("X-Trace-ID", expectedTraceId);
        request.setRequestURI("/api/test");
        request.setMethod("GET");

        doAnswer(invocation -> {
            String mdcTraceId = MDC.get("traceId");
            assertEquals(expectedTraceId, mdcTraceId);
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testHeaderPresenceInResponse() throws ServletException, IOException {
        request.setRequestURI("/api/test");
        request.setMethod("GET");

        filter.doFilter(request, response, filterChain);

        assertTrue(response.containsHeader("X-Trace-ID"));
    }
}
