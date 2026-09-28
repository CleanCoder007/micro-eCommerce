package com.ecommerce.common.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ErrorNotificationListener {

    private final AlertingService alertingService;

    @KafkaListener(
        topics = "${kafka.topics.error-events:error-events}",
        groupId = "${spring.application.name:ecommerce}-error-listener"
    )
    public void handleErrorNotification(ErrorNotificationEvent errorEvent) {
        try {
            log.error("Error notification received - EventID: {}, ServiceName: {}, ErrorType: {}, TraceID: {}",
                errorEvent.getEventId(), errorEvent.getServiceName(), errorEvent.getErrorType(), errorEvent.getTraceId());

            logToErrorAudit(errorEvent);

            if ("CRITICAL".equals(errorEvent.getSeverity()) || "HIGH".equals(errorEvent.getSeverity())) {
                triggerAlerts(errorEvent);
            }
        } catch (Exception ex) {
            log.error("Failed to handle error notification - EventID: {}", errorEvent.getEventId(), ex);
        }
    }

    private void logToErrorAudit(ErrorNotificationEvent errorEvent) {
        log.error("ERROR AUDIT - ServiceName: {}, ErrorType: {}, Message: {}, UserID: {}, OrderID: {}, TraceID: {}",
            errorEvent.getServiceName(),
            errorEvent.getErrorType(),
            errorEvent.getErrorMessage(),
            errorEvent.getUserId(),
            errorEvent.getOrderId(),
            errorEvent.getTraceId());
    }

    private void triggerAlerts(ErrorNotificationEvent errorEvent) {
        if ("CRITICAL".equals(errorEvent.getSeverity())) {
            if (isPaylmentOrInventoryError(errorEvent)) {
                alertingService.sendSlackAlert(formatSlackAlert(errorEvent));
                alertingService.sendPagerDutyAlert(errorEvent);
            }
            alertingService.sendEmailAlert(errorEvent);
        } else if ("HIGH".equals(errorEvent.getSeverity())) {
            alertingService.sendSlackAlert(formatSlackAlert(errorEvent));
        }
    }

    private boolean isPaylmentOrInventoryError(ErrorNotificationEvent errorEvent) {
        return "payment-service".equals(errorEvent.getServiceName()) ||
               "inventory-service".equals(errorEvent.getServiceName());
    }

    private String formatSlackAlert(ErrorNotificationEvent errorEvent) {
        return String.format(
            "🚨 *%s Error Alert*\n" +
            "Service: %s\n" +
            "Error Type: %s\n" +
            "Message: %s\n" +
            "TraceID: %s\n" +
            "OrderID: %s\n" +
            "Timestamp: %s",
            errorEvent.getSeverity(),
            errorEvent.getServiceName(),
            errorEvent.getErrorType(),
            errorEvent.getErrorMessage(),
            errorEvent.getTraceId(),
            errorEvent.getOrderId(),
            errorEvent.getTimestamp()
        );
    }
}
