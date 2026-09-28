package com.ecommerce.paymentservice.exception;

import com.ecommerce.common.dto.ErrorResponse;
import com.ecommerce.common.exception.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.timelimiter.RequestTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .error("NOT_FOUND")
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        log.warn("Validation failed for request: {}", request.getDescription(false));

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "VALIDATION_ERROR");
        response.put("message", "Validation failed");
        response.put("errorCode", "VALIDATION_FAILED");
        response.put("timestamp", LocalDateTime.now());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        response.put("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {
        log.error("Business exception: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("BAD_REQUEST")
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(CircuitBreakerException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerException(
            CircuitBreakerException ex,
            WebRequest request) {
        log.warn("Circuit breaker open for {}: {}", ex.getCircuitBreakerName(), ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error("SERVICE_UNAVAILABLE")
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCallNotPermitted(
            CallNotPermittedException ex,
            WebRequest request) {
        log.warn("Circuit breaker call not permitted: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error("CIRCUIT_BREAKER_OPEN")
            .message("Service temporarily unavailable - circuit breaker is open")
            .errorCode("CIRCUIT_BREAKER_OPEN")
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(TimeLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTimeLimitExceeded(
            TimeLimitExceededException ex,
            WebRequest request) {
        log.warn("Time limit exceeded: {} {}", ex.getTimeoutDuration(), ex.getTimeoutUnit());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.GATEWAY_TIMEOUT.value())
            .error("GATEWAY_TIMEOUT")
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(errorResponse);
    }

    @ExceptionHandler(RequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleRequestTimeout(
            RequestTimeoutException ex,
            WebRequest request) {
        log.warn("Request timeout: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.GATEWAY_TIMEOUT.value())
            .error("REQUEST_TIMEOUT")
            .message("Request timeout - operation took too long")
            .errorCode("REQUEST_TIMEOUT")
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(errorResponse);
    }

    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ErrorResponse> handleRetryableException(
            RetryableException ex,
            WebRequest request) {
        log.warn("Retryable exception (attempt {}/{}): {}",
            ex.getRetryAttempt(), ex.getMaxRetries(), ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error("TOO_MANY_REQUESTS")
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
    }

    @ExceptionHandler(DeadLetterException.class)
    public ResponseEntity<ErrorResponse> handleDeadLetterException(
            DeadLetterException ex,
            WebRequest request) {
        log.error("Dead letter exception on topic {}: {}", ex.getTopic(), ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("DEAD_LETTER_EXCEPTION")
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {
        log.error("Unexpected exception: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("INTERNAL_SERVER_ERROR")
            .message("An unexpected error occurred")
            .errorCode("INTERNAL_ERROR")
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
