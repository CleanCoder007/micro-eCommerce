# Correlation ID (X-Trace-ID) Guide

## Overview

The Correlation ID feature enables distributed request tracing across all microservices. Each request is assigned a unique trace ID that flows through the entire system, allowing logs to be correlated and traced end-to-end.

## How It Works

### Request Flow

1. **Request Reception**: When a request arrives at any service, the `RequestResponseLoggingFilter` checks for the `X-Trace-ID` header.
2. **ID Generation/Preservation**: 
   - If the header exists, it is preserved
   - If it doesn't exist, a new UUID is generated
3. **MDC Storage**: The trace ID is stored in the Mapped Diagnostic Context (MDC) for the current thread
4. **Log Inclusion**: All logs automatically include the trace ID via the log pattern
5. **Response Header**: The trace ID is added to the response headers
6. **Event Tracing**: The trace ID is included in all Kafka event headers for cross-service tracing

### MDC Integration

The trace ID is stored in the MDC with key `traceId`, making it available to:
- Console logs: `[%X{traceId}]` in the pattern
- Structured logging: Automatically included in Logstash encoder
- Custom logging: Access via `MDC.get("traceId")`

## Logback Configuration

The log pattern includes the trace ID:

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - [%X{traceId}] %msg%n</pattern>
```

Example log output:
```
10:15:23.456 [http-nio-8080-exec-1] INFO  com.ecommerce.order.service.OrderService - [a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6] Order created successfully
```

## Client Usage

### Setting Trace ID from Client

```bash
curl -H "X-Trace-ID: your-trace-id" http://localhost:8080/api/orders
```

### Receiving Trace ID in Response

The response will include the X-Trace-ID header:

```bash
HTTP/1.1 200 OK
X-Trace-ID: your-trace-id
Content-Type: application/json
```

## Tracing Across Services

When Service A calls Service B:

1. Service A's filter extracts/generates trace ID
2. Service A includes trace ID in HTTP headers when calling Service B
3. Service B's filter receives the same trace ID
4. All logs in both services contain the same trace ID

### RestTemplate Integration

To ensure trace ID is propagated in REST calls, services use an interceptor:

```java
// Automatically added by RequestResponseLoggingFilter
String traceId = MDC.get("traceId");
restTemplate.exchange(url, HttpMethod.GET, 
    new HttpEntity<>(createHeadersWithTraceId(traceId)), String.class);
```

## Kafka Integration

The trace ID is included in Kafka message headers:

```java
kafkaTemplate.send(message)
    .setHeader("X-Trace-ID", traceId)
    .setHeader("service-name", serviceName);
```

This enables tracing error events and async operations back to the original request.

## ELK Stack Queries

### Find all logs for a trace ID

```json
{
  "query": {
    "match": {
      "traceId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6"
    }
  }
}
```

### Find all requests by trace ID in a time range

```json
{
  "query": {
    "bool": {
      "must": [
        {"match": {"traceId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6"}},
        {"range": {"@timestamp": {"gte": "2024-01-01T00:00:00Z", "lte": "2024-01-02T00:00:00Z"}}}
      ]
    }
  }
}
```

### Find errors for a specific trace ID

```json
{
  "query": {
    "bool": {
      "must": [
        {"match": {"traceId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6"}},
        {"match": {"level": "ERROR"}}
      ]
    }
  }
}
```

## Debugging Guide

### 1. Start with the Request
- Find the original request in the logs
- Extract the X-Trace-ID from response headers or logs

### 2. Trace Through Services
```bash
# In ELK/Kibana
source: "order-service" AND traceId: "your-trace-id"
source: "payment-service" AND traceId: "your-trace-id"
source: "inventory-service" AND traceId: "your-trace-id"
```

### 3. Find Errors
```bash
# All errors for a trace
level: ERROR AND traceId: "your-trace-id"
```

### 4. Check Timing
- Compare timestamps across services to identify bottlenecks
- Look for large gaps between service calls

## Configuration

### application.yml

```yaml
logging:
  level:
    root: INFO
    com.ecommerce: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - [%X{traceId}] %msg%n"
```

### logback-spring.xml

The logback configuration automatically picks up the trace ID from MDC and includes it in all logs.

## Best Practices

1. **Always check the trace ID header** in API responses when debugging
2. **Pass trace ID to dependent services** when making REST calls
3. **Include trace ID in error reports** for better analysis
4. **Monitor trace ID propagation** in Kafka events
5. **Archive logs by trace ID** for long-term debugging

## Troubleshooting

### Trace ID not appearing in logs

- Verify `RequestResponseLoggingFilter` is registered (it's a @Component)
- Check the log pattern includes `%X{traceId}`
- Ensure MDC is not being cleared elsewhere

### Trace ID lost between services

- Verify RestTemplate includes X-Trace-ID header
- Check Kafka messages include header in producer config
- Ensure filter runs before business logic

### Performance impact

- Trace ID generation uses UUID which is O(1)
- MDC operations are thread-local and fast
- No significant performance overhead observed

## Related Components

- `RequestResponseLoggingFilter`: Extracts/generates trace ID
- `ErrorNotificationPublisher`: Includes trace ID in error events
- `ErrorNotificationListener`: Traces errors across services
- `AlertingService`: References trace ID in alerts
