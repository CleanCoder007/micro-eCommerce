package com.ecommerce.common.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class LogContextHolderTest {

    @BeforeEach
    void setUp() {
        LogContextHolder.clear();
        MDC.clear();
    }

    @Test
    void testInitializeContext() {
        LogContextHolder.initializeContext("customer-service");

        LogContext context = LogContextHolder.getContext();
        assertNotNull(context);
        assertNotNull(context.getCorrelationId());
        assertNotNull(context.getRequestId());
        assertEquals("customer-service", context.getServiceName());
    }

    @Test
    void testInitializeContextWithCorrelationId() {
        String correlationId = "test-correlation-123";

        LogContextHolder.initializeContext(correlationId, "order-service");

        LogContext context = LogContextHolder.getContext();
        assertEquals(correlationId, context.getCorrelationId());
        assertEquals("order-service", context.getServiceName());
    }

    @Test
    void testSetContext() {
        LogContext context = LogContext.builder()
            .correlationId("corr-123")
            .requestId("req-456")
            .serviceName("payment-service")
            .userId("user-789")
            .build();

        LogContextHolder.setContext(context);

        LogContext retrieved = LogContextHolder.getContext();
        assertEquals("corr-123", retrieved.getCorrelationId());
        assertEquals("user-789", retrieved.getUserId());
    }

    @Test
    void testMDCPopulation() {
        LogContextHolder.initializeContext("test-service");

        assertNotNull(MDC.get("correlationId"));
        assertNotNull(MDC.get("requestId"));
        assertEquals("test-service", MDC.get("serviceName"));
    }

    @Test
    void testSetUserId() {
        LogContextHolder.initializeContext("test-service");
        LogContextHolder.setUserId("user-123");

        assertEquals("user-123", LogContextHolder.getUserId());
        assertEquals("user-123", MDC.get("userId"));
    }

    @Test
    void testSetCustomerId() {
        LogContextHolder.initializeContext("test-service");
        LogContextHolder.setCustomerId("customer-456");

        assertEquals("customer-456", LogContextHolder.getCustomerId());
        assertEquals("customer-456", MDC.get("customerId"));
    }

    @Test
    void testAddMetadata() {
        LogContextHolder.initializeContext("test-service");
        LogContextHolder.addMetadata("key1", "value1");
        LogContextHolder.addMetadata("key2", 42);

        LogContext context = LogContextHolder.getContext();
        assertEquals("value1", context.getMetadata().get("key1"));
        assertEquals(42, context.getMetadata().get("key2"));
    }

    @Test
    void testClear() {
        LogContextHolder.initializeContext("test-service");
        LogContextHolder.setUserId("user-123");

        LogContextHolder.clear();

        assertNull(LogContextHolder.getContext());
        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("userId"));
    }

    @Test
    void testGetValuesFromMDCWhenContextNull() {
        LogContextHolder.clear();
        MDC.put("correlationId", "mdc-corr-123");

        assertEquals("mdc-corr-123", LogContextHolder.getCorrelationId());
    }

    @Test
    void testContextThreadLocal() {
        LogContextHolder.initializeContext("service-1");
        String correlationId1 = LogContextHolder.getCorrelationId();

        new Thread(() -> {
            assertNull(LogContextHolder.getContext());
            LogContextHolder.initializeContext("service-2");
            String correlationId2 = LogContextHolder.getCorrelationId();
            assertNotEquals(correlationId1, correlationId2);
        }).start();

        assertEquals(correlationId1, LogContextHolder.getCorrelationId());
    }
}
