package com.ecommerce.common.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LogContextIntegrationTest {

    @BeforeEach
    void setUp() {
        LogContextHolder.clear();
        MDC.clear();
    }

    @Test
    void testEndToEndContextFlow() {
        String correlationId = "e2e-test-123";
        String serviceName = "customer-service";

        LogContextHolder.initializeContext(correlationId, serviceName);
        LogContextHolder.setUserId("user-456");
        LogContextHolder.setCustomerId("customer-789");
        LogContextHolder.setComponentName("CustomerController");
        LogContextHolder.addMetadata("action", "create_order");
        LogContextHolder.addMetadata("itemCount", 5);

        LogContext context = LogContextHolder.getContext();

        assertEquals(correlationId, context.getCorrelationId());
        assertEquals("user-456", context.getUserId());
        assertEquals("customer-789", context.getCustomerId());
        assertEquals("CustomerController", context.getComponentName());
        assertEquals("create_order", context.getMetadata().get("action"));
        assertEquals(5, context.getMetadata().get("itemCount"));
    }

    @Test
    void testMDCConsistency() {
        LogContextHolder.initializeContext("mdc-test-123", "order-service");
        LogContextHolder.setUserId("user-999");

        String mdcCorrelationId = MDC.get("correlationId");
        String mdcUserId = MDC.get("userId");
        String mdcServiceName = MDC.get("serviceName");

        assertEquals("mdc-test-123", mdcCorrelationId);
        assertEquals("user-999", mdcUserId);
        assertEquals("order-service", mdcServiceName);
    }

    @Test
    void testExceptionContextWithLogContext() {
        LogContextHolder.initializeContext("exc-test-123", "payment-service");
        LogContextHolder.setUserId("user-111");
        LogContextHolder.setCustomerId("cust-222");

        try {
            throw new IllegalArgumentException("Invalid payment amount");
        } catch (IllegalArgumentException e) {
            ExceptionContext excContext = ExceptionContext.from(e,
                LogContextHolder.getCorrelationId(),
                LogContextHolder.getRequestId());

            excContext.setUserId(LogContextHolder.getUserId());
            excContext.addContextData("customerId", LogContextHolder.getCustomerId());

            assertEquals("exc-test-123", excContext.getCorrelationId());
            assertEquals("user-111", excContext.getUserId());
            assertEquals("cust-222", excContext.getContextData().get("customerId"));
        }
    }

    @Test
    void testContextPropagationAcrossMethodCalls() {
        LogContextHolder.initializeContext("prop-test-123", "inventory-service");
        LogContextHolder.setComponentName("InventoryService");

        performInnerOperation();

        LogContext context = LogContextHolder.getContext();
        assertEquals("prop-test-123", context.getCorrelationId());
        assertEquals("InventoryService", context.getComponentName());
    }

    private void performInnerOperation() {
        LogContextHolder.setComponentName("InnerComponent");
        LogContext context = LogContextHolder.getContext();
        assertEquals("InnerComponent", context.getComponentName());
    }

    @Test
    void testContextIsolationAcrossThreads() throws InterruptedException {
        LogContextHolder.initializeContext("thread-test-1", "service-1");
        LogContextHolder.setUserId("user-1");

        AtomicBoolean threadTestPassed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                LogContext mainContext = LogContextHolder.getContext();
                assertNull(mainContext, "Child thread should not have main thread's context");

                LogContextHolder.initializeContext("thread-test-2", "service-2");
                LogContextHolder.setUserId("user-2");

                LogContext childContext = LogContextHolder.getContext();
                assertEquals("thread-test-2", childContext.getCorrelationId());
                assertEquals("user-2", childContext.getUserId());
                threadTestPassed.set(true);
            } finally {
                latch.countDown();
            }
        }).start();

        latch.await();

        assertTrue(threadTestPassed.get());

        LogContext mainContext = LogContextHolder.getContext();
        assertEquals("thread-test-1", mainContext.getCorrelationId());
        assertEquals("user-1", mainContext.getUserId());
    }

    @Test
    void testContextToMapConversion() {
        LocalDateTime now = LocalDateTime.now();

        LogContext context = LogContext.builder()
            .correlationId("map-test-123")
            .requestId("req-456")
            .userId("user-789")
            .customerId("cust-012")
            .serviceName("test-service")
            .componentName("TestComponent")
            .timestamp(now)
            .build();

        context.addMetadata("key1", "value1");
        context.addMetadata("key2", 42);

        Map<String, Object> map = context.toMap();

        assertEquals("map-test-123", map.get("correlationId"));
        assertEquals("user-789", map.get("userId"));
        assertEquals("cust-012", map.get("customerId"));
        assertEquals("test-service", map.get("serviceName"));
        assertNotNull(map.get("metadata"));
    }

    @Test
    void testClearAndReinitialize() {
        LogContextHolder.initializeContext("first-context", "service-1");
        assertEquals("first-context", LogContextHolder.getCorrelationId());

        LogContextHolder.clear();
        assertNull(LogContextHolder.getContext());

        LogContextHolder.initializeContext("second-context", "service-2");
        assertEquals("second-context", LogContextHolder.getCorrelationId());
    }

    @Test
    void testNullSafeOperations() {
        LogContextHolder.clear();

        assertNull(LogContextHolder.getContext());
        assertNull(LogContextHolder.getCorrelationId());
        assertNull(LogContextHolder.getUserId());

        LogContextHolder.addMetadata("key", "value");
        LogContextHolder.setUserId("user-123");

        LogContextHolder.clear();
    }
}
