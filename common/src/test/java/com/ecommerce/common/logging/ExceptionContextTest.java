package com.ecommerce.common.logging;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionContextTest {

    @Test
    void testExceptionContextCreation() {
        ExceptionContext context = ExceptionContext.builder()
            .exceptionType("java.lang.NullPointerException")
            .message("Value cannot be null")
            .correlationId("corr-123")
            .requestId("req-456")
            .build();

        assertEquals("java.lang.NullPointerException", context.getExceptionType());
        assertEquals("Value cannot be null", context.getMessage());
        assertEquals("corr-123", context.getCorrelationId());
    }

    @Test
    void testExceptionContextFromThrowable() {
        try {
            throw new IllegalArgumentException("Invalid input");
        } catch (IllegalArgumentException e) {
            ExceptionContext context = ExceptionContext.from(e, "corr-123", "req-456");

            assertEquals("java.lang.IllegalArgumentException", context.getExceptionType());
            assertEquals("Invalid input", context.getMessage());
            assertEquals("corr-123", context.getCorrelationId());
            assertNotNull(context.getStackTrace());
            assertFalse(context.getStackTrace().isEmpty());
        }
    }

    @Test
    void testStackTraceExtraction() {
        try {
            innerMethod();
        } catch (RuntimeException e) {
            ExceptionContext context = ExceptionContext.from(e, "corr-123", "req-456");

            List<String> stackTrace = context.getStackTrace();
            assertNotNull(stackTrace);
            assertTrue(stackTrace.size() > 0);
            assertTrue(stackTrace.size() <= 21); // 20 frames + "... N more"
        }
    }

    private void innerMethod() {
        deeperMethod();
    }

    private void deeperMethod() {
        throw new RuntimeException("Test exception");
    }

    @Test
    void testRootCauseExtraction() {
        try {
            throw new RuntimeException("Outer", new IllegalStateException("Inner", new NullPointerException("Root")));
        } catch (RuntimeException e) {
            ExceptionContext context = ExceptionContext.from(e, "corr-123", "req-456");

            String rootCause = context.getRootCause();
            assertTrue(rootCause.contains("NullPointerException"));
            assertTrue(rootCause.contains("Root"));
        }
    }

    @Test
    void testAddContextData() {
        ExceptionContext context = ExceptionContext.builder()
            .exceptionType("TestException")
            .message("Test message")
            .build();

        context.addContextData("userId", "user-123");
        context.addContextData("requestId", "req-456");

        assertEquals("user-123", context.getContextData().get("userId"));
        assertEquals("req-456", context.getContextData().get("requestId"));
    }

    @Test
    void testToMap() {
        ExceptionContext context = ExceptionContext.builder()
            .exceptionType("TestException")
            .message("Test message")
            .correlationId("corr-123")
            .requestId("req-456")
            .userId("user-789")
            .build();

        Map<String, Object> map = context.toMap();

        assertEquals("TestException", map.get("exceptionType"));
        assertEquals("Test message", map.get("message"));
        assertEquals("corr-123", map.get("correlationId"));
        assertEquals("user-789", map.get("userId"));
    }

    @Test
    void testNestedExceptionHandling() {
        try {
            try {
                throw new NullPointerException("Inner exception");
            } catch (NullPointerException e) {
                throw new IllegalStateException("Outer exception", e);
            }
        } catch (IllegalStateException e) {
            ExceptionContext context = ExceptionContext.from(e, "corr-123", "req-456");

            assertNotNull(context.getRootCause());
            assertTrue(context.getRootCause().contains("NullPointerException"));
        }
    }
}
