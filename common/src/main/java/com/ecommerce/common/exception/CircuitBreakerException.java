package com.ecommerce.common.exception;

public class CircuitBreakerException extends RuntimeException {
    private final String errorCode;
    private final String circuitBreakerName;
    private final String state;

    public CircuitBreakerException(String message, String circuitBreakerName, String state) {
        super(message);
        this.errorCode = "CIRCUIT_BREAKER_OPEN";
        this.circuitBreakerName = circuitBreakerName;
        this.state = state;
    }

    public CircuitBreakerException(String message, String circuitBreakerName, String state, Throwable cause) {
        super(message, cause);
        this.errorCode = "CIRCUIT_BREAKER_OPEN";
        this.circuitBreakerName = circuitBreakerName;
        this.state = state;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getCircuitBreakerName() {
        return circuitBreakerName;
    }

    public String getState() {
        return state;
    }
}
