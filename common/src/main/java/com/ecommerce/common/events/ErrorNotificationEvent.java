package com.ecommerce.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorNotificationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("service_name")
    private String serviceName;

    @JsonProperty("error_type")
    private String errorType;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("error_details")
    private String errorDetails;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("stacktrace")
    private String stacktrace;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventId;
        private String orderId;
        private String serviceName;
        private String errorType;
        private String errorMessage;
        private String errorDetails;
        private String traceId;
        private String userId;
        private String transactionId;
        private String severity;
        private LocalDateTime timestamp;
        private String stacktrace;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder errorDetails(String errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder severity(String severity) {
            this.severity = severity;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder stacktrace(String stacktrace) {
            this.stacktrace = stacktrace;
            return this;
        }

        public ErrorNotificationEvent build() {
            return new ErrorNotificationEvent(
                eventId, orderId, serviceName, errorType, errorMessage,
                errorDetails, traceId, userId, transactionId, severity,
                timestamp, stacktrace
            );
        }
    }
}
