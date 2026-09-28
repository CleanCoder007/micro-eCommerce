# Error Notification Guide

## Overview

The error notification system provides event-driven error handling with automatic alerts for critical issues. When services encounter errors, events are published to Kafka and consumed by the error handler, which triggers appropriate notifications.

## Architecture

### Components

1. **ErrorNotificationEvent**: Data model for error events
2. **ErrorNotificationPublisher**: Publishes errors to Kafka
3. **ErrorNotificationListener**: Consumes error events and triggers alerts
4. **AlertingService**: Sends notifications (Slack, Email, PagerDuty)
5. **GlobalErrorExceptionHandler**: Captures exceptions and publishes error events

## Error Event Structure

```json
{
  "event_id": "uuid",
  "order_id": "ORDER-12345",
  "service_name": "payment-service",
  "error_type": "PAYMENT_DECLINED",
  "error_message": "Card declined due to insufficient funds",
  "error_details": "Stack trace and additional context",
  "trace_id": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "user_id": "USER-789",
  "transaction_id": "TXN-456",
  "severity": "CRITICAL|HIGH|MEDIUM",
  "timestamp": "2024-01-15T10:30:00Z",
  "stacktrace": "Full stack trace"
}
```

## Error Types and Severity Levels

### CRITICAL (Immediate Action Required)

- **DATABASE_CONNECTION_ERROR**: Database unreachable or connection pool exhausted
- **PAYMENT_GATEWAY_FAILURE**: Payment processor down or unreachable
- **DATA_LOSS_ERROR**: Data integrity violation or inconsistency
- **SERVICE_UNAVAILABLE**: Dependency service is down
- **CONFIGURATION_ERROR**: Critical configuration missing or invalid

Actions:
- PagerDuty incident created
- Slack alert in #alerts channel
- Email to operations team
- Automatic service health check downgrade

### HIGH (Attention Required)

- **PAYMENT_DECLINED**: Customer payment failed
- **INVENTORY_SHORTAGE**: Not enough stock for order
- **BUSINESS_RULE_VIOLATION**: Order validation failed
- **THIRD_PARTY_TIMEOUT**: External API timeout
- **AUTHENTICATION_FAILURE**: User authentication failed

Actions:
- Slack alert in #alerts channel
- Email to operations team
- Logged in error audit table
- Automatic retry for transient failures

### MEDIUM (Monitor and Log)

- **VALIDATION_ERROR**: Request validation failed
- **RETRY_EXHAUSTED**: Max retry attempts exceeded
- **RATE_LIMIT_EXCEEDED**: API rate limit hit
- **CACHE_MISS**: Cache lookup failed

Actions:
- Logged in error audit table
- Included in daily error report
- Metrics updated

## Error Notification Routing

### By Severity and Service

```
CRITICAL Errors:
├── All Services
│   ├── Slack: #alerts
│   ├── Email: ops-team@company.com
│   ├── PagerDuty: Create incident
│   └── Error Audit: Store for forensics
│
└── Payment/Inventory Services
    ├── PagerDuty: Create incident (escalated)
    └── Slack: #critical-alerts (immediate)

HIGH Errors:
├── All Services
│   ├── Slack: #alerts
│   ├── Email: ops-team@company.com
│   └── Error Audit: Store for forensics
│
└── Business Errors (Payment Declined, etc)
    ├── Customer Notification: Email to customer
    └── Analytics: Track for reporting

MEDIUM Errors:
├── Error Audit: Store for forensics
└── Metrics: Update error counters
```

## Alerting Channels

### Slack Integration

**Configuration**:
```yaml
alerting:
  slack:
    enabled: true
    webhook-url: "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
```

**Alert Format**:
```
🚨 *CRITICAL Error Alert*
Service: payment-service
Error Type: PAYMENT_GATEWAY_FAILURE
Message: Connection timeout to payment processor
TraceID: a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6
OrderID: ORDER-12345
Timestamp: 2024-01-15T10:30:00Z
```

### Email Notifications

**Configuration**:
```yaml
alerting:
  email:
    enabled: true
    recipients: "ops@company.com,payments-team@company.com"
```

**Email Content**:
- Subject: `[ecommerce] CRITICAL Error Alert - PAYMENT_GATEWAY_FAILURE`
- Body includes: Service, Error Type, Message, Trace ID, Order ID, Timestamp, Full Details
- Attachments: Error logs for the trace ID

### PagerDuty Integration

**Configuration**:
```yaml
alerting:
  pagerduty:
    enabled: true
    integration-key: "YOUR_INTEGRATION_KEY"
```

**Incident Details**:
- Title: Service - Error Type
- Severity: critical / error / warning / info
- Source: Service Name
- Custom Details: Trace ID, Order ID, Error Message, User ID

## Recovery Procedures

### Payment Processing Error

1. Check payment gateway status
2. Review transaction details via trace ID
3. Retry with exponential backoff
4. If persistent, escalate to payment team
5. Update customer with status

### Inventory Error

1. Verify database connectivity
2. Check stock levels
3. Review recent inventory operations
4. Rollback last inventory transaction if needed
5. Notify warehouse team

### Database Error

1. Check database connectivity
2. Review connection pool status
3. Check for database locks
4. Review disk space
5. Contact DBA team

### Service Unavailability

1. Check service health endpoint
2. Review recent deployments
3. Check for resource exhaustion
4. Verify network connectivity
5. Restart service if needed

## Monitoring Error Events

### Kafka Topic Configuration

```yaml
kafka:
  topics:
    error-events: error-events
  consumer:
    group-id: "${spring.application.name}-error-listener"
    max-poll-records: 100
    max-poll-interval-ms: 300000
```

### Error Event Metrics

Metrics are exposed at `/actuator/metrics`:

- `errors.total`: Total errors published
- `errors.by_type`: Errors grouped by error type
- `errors.by_severity`: Errors grouped by severity
- `alerts.sent`: Total alerts sent
- `alerts.by_channel`: Alerts sent by channel (Slack, Email, PagerDuty)

### ELK Stack Queries

**Find all errors in last hour**:
```json
{
  "query": {
    "range": {
      "@timestamp": {"gte": "now-1h"}
    }
  }
}
```

**Find critical errors for a service**:
```json
{
  "query": {
    "bool": {
      "must": [
        {"match": {"service_name": "payment-service"}},
        {"match": {"severity": "CRITICAL"}}
      ]
    }
  }
}
```

**Find errors for an order**:
```json
{
  "query": {
    "match": {"order_id": "ORDER-12345"}
  }
}
```

## Configuration Examples

### Full Configuration

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: kafka:9092
    topics:
      error-events: error-events

alerting:
  slack:
    enabled: true
    webhook-url: "${SLACK_WEBHOOK_URL}"
  email:
    enabled: true
    recipients: "ops@company.com"
  pagerduty:
    enabled: true
    integration-key: "${PAGERDUTY_KEY}"

logging:
  level:
    com.ecommerce.common.events: DEBUG
```

### Docker Compose

```yaml
services:
  kafka:
    image: confluentinc/cp-kafka:latest
    environment:
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_LOG_RETENTION_MS: "86400000"
```

## Troubleshooting

### Errors not being published

1. Verify Kafka connectivity
2. Check error topic exists: `kafka-topics --list --bootstrap-server kafka:9092`
3. Review service logs for ErrorNotificationPublisher errors
4. Verify GlobalErrorExceptionHandler is configured

### Alerts not being sent

1. Check Slack webhook URL is valid
2. Verify email configuration and recipients
3. Check PagerDuty integration key
4. Review AlertingService logs

### Trace ID not in error events

1. Verify RequestResponseLoggingFilter is active
2. Check MDC.get("traceId") returns value
3. Ensure GlobalErrorExceptionHandler captures trace ID
4. Review error event creation

## Best Practices

1. **Always include trace ID** when reporting errors
2. **Test alert channels** in staging before production
3. **Monitor alert fatigue** and adjust severity levels
4. **Archive error events** for compliance and auditing
5. **Review error trends** weekly to identify patterns
6. **Set up dashboards** for error metrics
7. **Document recovery procedures** for each error type
8. **Keep pagerduty escalation paths** up to date

## Related Components

- `ErrorNotificationEvent`: Error event data model
- `ErrorNotificationPublisher`: Kafka producer
- `ErrorNotificationListener`: Kafka consumer
- `AlertingService`: Multi-channel alerting
- `GlobalErrorExceptionHandler`: Exception capture and publishing
