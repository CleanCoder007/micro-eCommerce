package com.ecommerce.common.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LogContextFilterIntegrationTest {

    @InjectMocks
    private LogContextFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        LogContextHolder.clear();
        MDC.clear();

        filter = new LogContextFilter();
        filter.initServletContext(null);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @Test
    void testFilterInitializesContext() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/customers/123");
        request.setRemoteAddr("192.168.1.1");

        filter.doFilter(request, response, filterChain);

        assertNotNull(MDC.get("correlationId"));
        assertNotNull(MDC.get("requestId"));
        assertEquals("GET", MDC.get("method"));
        assertEquals("/api/customers/123", MDC.get("path"));
    }

    @Test
    void testFilterPreservesProvidedCorrelationId() throws ServletException, IOException {
        String providedCorrelationId = "provided-corr-123";
        request.addHeader("X-Correlation-Id", providedCorrelationId);
        request.setMethod("POST");
        request.setRequestURI("/api/orders");

        filter.doFilter(request, response, filterChain);

        assertEquals(providedCorrelationId, MDC.get("correlationId"));
        assertEquals(providedCorrelationId, response.getHeader("X-Correlation-Id"));
    }

    @Test
    void testFilterGeneratesNewCorrelationIdIfNotProvided() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/products");

        filter.doFilter(request, response, filterChain);

        String correlationId = MDC.get("correlationId");
        assertNotNull(correlationId);
        assertNotEquals("", correlationId);
        assertEquals(correlationId, response.getHeader("X-Correlation-Id"));
    }

    @Test
    void testFilterClearsContextAfterRequest() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/test");

        filter.doFilter(request, response, filterChain);

        // Context should be cleared after filter
        assertNull(LogContextHolder.getContext());
    }

    @Test
    void testFilterSkipsActuatorEndpoints() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/actuator/health");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void testFilterSkipsSwaggerEndpoints() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/swagger-ui.html");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void testFilterProcessesApiEndpoints() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/customers");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void testContextPropagationThroughRequest() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/orders");
        request.addHeader("X-Correlation-Id", "test-corr-123");

        filter.doFilter(request, response, filterChain);

        // Verify response headers include correlation ID
        assertEquals("test-corr-123", response.getHeader("X-Correlation-Id"));
        assertNotNull(response.getHeader("X-Request-Id"));
    }

    @Test
    void testMultipleMetadataAddition() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/payments");
        request.setContentType("application/json");

        filter.doFilter(request, response, filterChain);

        assertEquals("POST", MDC.get("method"));
        assertEquals("/api/payments", MDC.get("path"));
        assertEquals("192.168.1.1", MDC.get("remoteAddr"));
    }
}
