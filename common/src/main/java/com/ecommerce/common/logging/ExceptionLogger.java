package com.ecommerce.common.logging;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@UtilityClass
@Slf4j
public class ExceptionLogger {

    public static void logException(Throwable throwable) {
        logException(throwable, null);
    }

    public static void logException(Throwable throwable, Map<String, Object> additionalContext) {
        LogContext logContext = LogContextHolder.getContext();
        String correlationId = logContext != null ? logContext.getCorrelationId() : LogContextHolder.getCorrelationId();
        String requestId = logContext != null ? logContext.getRequestId() : LogContextHolder.getRequestId();

        ExceptionContext exceptionContext = ExceptionContext.from(throwable, correlationId, requestId);

        if (additionalContext != null) {
            additionalContext.forEach(exceptionContext::addContextData);
        }

        if (logContext != null) {
            exceptionContext.setUserId(logContext.getUserId());
        }

        log.error("Exception occurred: {} - {}",
            exceptionContext.getExceptionType(),
            exceptionContext.getMessage(),
            throwable);

        log.debug("Exception context: {}", exceptionContext.toMap());
    }

    public static void logCriticalException(Throwable throwable, String message) {
        LogContext logContext = LogContextHolder.getContext();
        String correlationId = logContext != null ? logContext.getCorrelationId() : LogContextHolder.getCorrelationId();
        String requestId = logContext != null ? logContext.getRequestId() : LogContextHolder.getRequestId();

        ExceptionContext exceptionContext = ExceptionContext.from(throwable, correlationId, requestId);

        log.error("CRITICAL: {} - {} [{}]",
            message,
            exceptionContext.getExceptionType(),
            exceptionContext.getCorrelationId(),
            throwable);
    }
}
