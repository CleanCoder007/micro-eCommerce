package com.ecommerce.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

@Slf4j
public class LogContextHolder {
    private static final ThreadLocal<LogContext> contextHolder = new ThreadLocal<>();
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";
    private static final String CUSTOMER_ID_KEY = "customerId";
    private static final String SERVICE_NAME_KEY = "serviceName";
    private static final String COMPONENT_NAME_KEY = "componentName";

    public static void initializeContext(String serviceName) {
        LogContext context = LogContext.builder()
            .correlationId(UUID.randomUUID().toString())
            .requestId(UUID.randomUUID().toString())
            .serviceName(serviceName)
            .build();
        setContext(context);
    }

    public static void initializeContext(String correlationId, String serviceName) {
        LogContext context = LogContext.builder()
            .correlationId(correlationId)
            .requestId(UUID.randomUUID().toString())
            .serviceName(serviceName)
            .build();
        setContext(context);
    }

    public static void setContext(LogContext context) {
        if (context == null) {
            clear();
            return;
        }
        contextHolder.set(context);
        populateMDC(context);
    }

    public static LogContext getContext() {
        return contextHolder.get();
    }

    public static String getCorrelationId() {
        LogContext context = getContext();
        return context != null ? context.getCorrelationId() : MDC.get(CORRELATION_ID_KEY);
    }

    public static String getRequestId() {
        LogContext context = getContext();
        return context != null ? context.getRequestId() : MDC.get(REQUEST_ID_KEY);
    }

    public static String getUserId() {
        LogContext context = getContext();
        return context != null ? context.getUserId() : MDC.get(USER_ID_KEY);
    }

    public static String getCustomerId() {
        LogContext context = getContext();
        return context != null ? context.getCustomerId() : MDC.get(CUSTOMER_ID_KEY);
    }

    public static void setUserId(String userId) {
        LogContext context = getContext();
        if (context != null) {
            context.setUserId(userId);
            MDC.put(USER_ID_KEY, userId);
        }
    }

    public static void setCustomerId(String customerId) {
        LogContext context = getContext();
        if (context != null) {
            context.setCustomerId(customerId);
            MDC.put(CUSTOMER_ID_KEY, customerId);
        }
    }

    public static void setComponentName(String componentName) {
        LogContext context = getContext();
        if (context != null) {
            context.setComponentName(componentName);
            MDC.put(COMPONENT_NAME_KEY, componentName);
        }
    }

    public static void addMetadata(String key, Object value) {
        LogContext context = getContext();
        if (context != null) {
            context.addMetadata(key, value);
        }
    }

    public static void clear() {
        contextHolder.remove();
        MDC.clear();
    }

    private static void populateMDC(LogContext context) {
        if (context.getCorrelationId() != null) {
            MDC.put(CORRELATION_ID_KEY, context.getCorrelationId());
        }
        if (context.getRequestId() != null) {
            MDC.put(REQUEST_ID_KEY, context.getRequestId());
        }
        if (context.getUserId() != null) {
            MDC.put(USER_ID_KEY, context.getUserId());
        }
        if (context.getCustomerId() != null) {
            MDC.put(CUSTOMER_ID_KEY, context.getCustomerId());
        }
        if (context.getServiceName() != null) {
            MDC.put(SERVICE_NAME_KEY, context.getServiceName());
        }
        if (context.getComponentName() != null) {
            MDC.put(COMPONENT_NAME_KEY, context.getComponentName());
        }
    }
}
