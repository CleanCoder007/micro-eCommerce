package com.ecommerce.common.exception;

public class RetryableException extends RuntimeException {
    private final String errorCode;
    private final int retryAttempt;
    private final int maxRetries;

    public RetryableException(String message, int retryAttempt, int maxRetries) {
        super(message);
        this.errorCode = "RETRYABLE_ERROR";
        this.retryAttempt = retryAttempt;
        this.maxRetries = maxRetries;
    }

    public RetryableException(String message, int retryAttempt, int maxRetries, Throwable cause) {
        super(message, cause);
        this.errorCode = "RETRYABLE_ERROR";
        this.retryAttempt = retryAttempt;
        this.maxRetries = maxRetries;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getRetryAttempt() {
        return retryAttempt;
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
