package com.ecommerce.common.exception;

public class DeadLetterException extends RuntimeException {
    private final String errorCode;
    private final String topic;
    private final String messageKey;

    public DeadLetterException(String message, String topic, String messageKey) {
        super(message);
        this.errorCode = "DEAD_LETTER_EXCEPTION";
        this.topic = topic;
        this.messageKey = messageKey;
    }

    public DeadLetterException(String message, String topic, String messageKey, Throwable cause) {
        super(message, cause);
        this.errorCode = "DEAD_LETTER_EXCEPTION";
        this.topic = topic;
        this.messageKey = messageKey;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
