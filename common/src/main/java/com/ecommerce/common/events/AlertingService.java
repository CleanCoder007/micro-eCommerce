package com.ecommerce.common.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AlertingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${alerting.slack.enabled:false}")
    private boolean slackEnabled;

    @Value("${alerting.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${alerting.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${alerting.email.recipients:}")
    private String emailRecipients;

    @Value("${alerting.pagerduty.enabled:false}")
    private boolean pagerDutyEnabled;

    @Value("${alerting.pagerduty.integration-key:}")
    private String pagerDutyIntegrationKey;

    @Value("${spring.application.name:ecommerce}")
    private String applicationName;

    public AlertingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendSlackAlert(String message) {
        if (!slackEnabled || slackWebhookUrl.isEmpty()) {
            log.debug("Slack alerting is disabled");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            payload.put("username", "ecommerce-alerts");
            payload.put("channel", "#alerts");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForObject(slackWebhookUrl, request, String.class);

            log.info("Slack alert sent successfully");
        } catch (Exception ex) {
            log.error("Failed to send Slack alert", ex);
        }
    }

    public void sendEmailAlert(ErrorNotificationEvent errorEvent) {
        if (!emailEnabled || emailRecipients.isEmpty()) {
            log.debug("Email alerting is disabled");
            return;
        }

        try {
            String subject = String.format("[%s] %s Error Alert - %s",
                applicationName, errorEvent.getSeverity(), errorEvent.getErrorType());

            String body = formatEmailBody(errorEvent);

            log.info("Email alert prepared for recipients: {}", emailRecipients);
            log.debug("Email alert - Subject: {}, Body: {}", subject, body);
        } catch (Exception ex) {
            log.error("Failed to prepare email alert", ex);
        }
    }

    public void sendPagerDutyAlert(ErrorNotificationEvent errorEvent) {
        if (!pagerDutyEnabled || pagerDutyIntegrationKey.isEmpty()) {
            log.debug("PagerDuty alerting is disabled");
            return;
        }

        try {
            ObjectNode pagerDutyPayload = objectMapper.createObjectNode();
            pagerDutyPayload.put("routing_key", pagerDutyIntegrationKey);
            pagerDutyPayload.put("event_action", "trigger");

            ObjectNode dedup = objectMapper.createObjectNode();
            dedup.put("summary", String.format("%s - %s", errorEvent.getServiceName(), errorEvent.getErrorType()));
            dedup.put("severity", mapSeverity(errorEvent.getSeverity()));
            dedup.put("source", errorEvent.getServiceName());
            dedup.put("timestamp", errorEvent.getTimestamp().toString());

            ObjectNode customDetails = objectMapper.createObjectNode();
            customDetails.put("trace_id", errorEvent.getTraceId());
            customDetails.put("order_id", errorEvent.getOrderId());
            customDetails.put("error_message", errorEvent.getErrorMessage());
            customDetails.put("user_id", errorEvent.getUserId());

            dedup.set("custom_details", customDetails);
            pagerDutyPayload.set("dedup_key", objectMapper.createObjectNode().put("key", errorEvent.getEventId()));
            pagerDutyPayload.set("payload", dedup);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(pagerDutyPayload), headers);
            restTemplate.postForObject("https://events.pagerduty.com/v2/enqueue", request, String.class);

            log.info("PagerDuty alert sent successfully");
        } catch (Exception ex) {
            log.error("Failed to send PagerDuty alert", ex);
        }
    }

    private String formatEmailBody(ErrorNotificationEvent errorEvent) {
        return String.format(
            "Service: %s\n" +
            "Error Type: %s\n" +
            "Message: %s\n" +
            "Severity: %s\n" +
            "TraceID: %s\n" +
            "OrderID: %s\n" +
            "UserID: %s\n" +
            "Timestamp: %s\n\n" +
            "Details:\n%s",
            errorEvent.getServiceName(),
            errorEvent.getErrorType(),
            errorEvent.getErrorMessage(),
            errorEvent.getSeverity(),
            errorEvent.getTraceId(),
            errorEvent.getOrderId(),
            errorEvent.getUserId(),
            errorEvent.getTimestamp(),
            errorEvent.getErrorDetails()
        );
    }

    private String mapSeverity(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "critical";
            case "HIGH" -> "error";
            case "MEDIUM" -> "warning";
            default -> "info";
        };
    }
}
