package com.ecommerce.common.exception;

public class TimeLimitExceededException extends RuntimeException {
    private final String errorCode;
    private final long timeoutDuration;
    private final String timeoutUnit;

    public TimeLimitExceededException(String message, long timeoutDuration, String timeoutUnit) {
        super(message);
        this.errorCode = "TIMEOUT_EXCEEDED";
        this.timeoutDuration = timeoutDuration;
        this.timeoutUnit = timeoutUnit;
    }

    public TimeLimitExceededException(String message, long timeoutDuration, String timeoutUnit, Throwable cause) {
        super(message, cause);
        this.errorCode = "TIMEOUT_EXCEEDED";
        this.timeoutDuration = timeoutDuration;
        this.timeoutUnit = timeoutUnit;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public long getTimeoutDuration() {
        return timeoutDuration;
    }

    public String getTimeoutUnit() {
        return timeoutUnit;
    }
}
