package com.ecommerce.common.exception;

import com.ecommerce.common.dto.ErrorResponse;
import com.ecommerce.common.events.ErrorNotificationEvent;
import com.ecommerce.common.events.ErrorNotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalErrorExceptionHandler extends ResponseEntityExceptionHandler {

    private final ErrorNotificationPublisher errorNotificationPublisher;

    @Value("${spring.application.name:ecommerce}")
    private String serviceName;

    private static final Map<Class<?>, ErrorConfig> ERROR_CONFIGS = new HashMap<>();

    static {
        ERROR_CONFIGS.put(ResourceNotFoundException.class,
            new ErrorConfig(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "HIGH"));
        ERROR_CONFIGS.put(BusinessException.class,
            new ErrorConfig(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION", "HIGH"));
        ERROR_CONFIGS.put(ValidationException.class,
            new ErrorConfig(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "MEDIUM"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return handleCustomException(ex, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "HIGH");
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        return handleCustomException(ex, HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION", "HIGH");
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        return handleCustomException(ex, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "MEDIUM");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return handleCustomException(ex, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "CRITICAL");
    }

    private ResponseEntity<ErrorResponse> handleCustomException(
            Exception ex, HttpStatus status, String errorType, String severity) {

        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();

        log.error("Exception occurred - ErrorType: {}, Severity: {}, TraceID: {}",
            errorType, severity, traceId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .message(ex.getMessage())
            .status(status.value())
            .timestamp(LocalDateTime.now())
            .traceId(traceId)
            .build();

        publishErrorEvent(ex, errorType, severity, traceId);

        return ResponseEntity.status(status).body(errorResponse);
    }

    private void publishErrorEvent(Exception ex, String errorType, String severity, String traceId) {
        try {
            ErrorNotificationEvent event = ErrorNotificationEvent.builder()
                .serviceName(serviceName)
                .errorType(errorType)
                .errorMessage(ex.getMessage())
                .errorDetails(getStackTrace(ex))
                .traceId(traceId)
                .severity(severity)
                .timestamp(LocalDateTime.now())
                .build();

            if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
                errorNotificationPublisher.publishError(event);
            }
        } catch (Exception publishEx) {
            log.error("Failed to publish error notification - TraceID: {}", traceId, publishEx);
        }
    }

    private String getStackTrace(Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");

        for (StackTraceElement element : ex.getStackTrace()) {
            if (element.getClassName().startsWith("com.ecommerce")) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
        }

        return sb.toString();
    }

    private static class ErrorConfig {
        HttpStatus status;
        String errorType;
        String severity;

        ErrorConfig(HttpStatus status, String errorType, String severity) {
            this.status = status;
            this.errorType = errorType;
            this.severity = severity;
        }
    }
}
