# Structured Logging Guide

## Overview

This guide explains the structured logging system with context propagation that enables correlation tracking, user attribution, and efficient log searching across the micro-ecommerce platform.

## Architecture

### Components

1. **LogContext** - Holds request-scoped logging data
2. **LogContextHolder** - ThreadLocal storage for context
3. **LogContextFilter** - Initializes context for HTTP requests
4. **ExceptionContext** - Captures exception details with context
5. **GlobalExceptionHandler** - Centralized exception logging

### Flow

```
HTTP Request
    ↓
LogContextFilter (initializes correlation ID, request ID)
    ↓
LogContextHolder (stores in ThreadLocal)
    ↓
Application processing (context available throughout request)
    ↓
Exception → GlobalExceptionHandler (logs with context)
    ↓
HTTP Response (correlation ID in headers)
    ↓
LogContext cleared from ThreadLocal
    ↓
Logstash collects JSON logs
    ↓
Elasticsearch indexes by correlation ID
```

## Log Structure

### Standard Log Fields

Each log entry includes:

```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "message": "Customer created successfully",
  "level": "INFO",
  "logger_name": "com.ecommerce.customer.service.CustomerService",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "requestId": "660e8400-e29b-41d4-a716-446655440001",
  "userId": "user-123",
  "customerId": "cust-456",
  "serviceName": "customer-service",
  "componentName": "CustomerService",
  "environment": "production",
  "thread_name": "http-nio-8080-exec-1",
  "metadata": {
    "action": "create",
    "customerId": "cust-456",
    "email": "customer@example.com"
  }
}
```

### Exception Log Fields

```json
{
  "level": "ERROR",
  "message": "Database error during customer creation",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-123",
  "exceptionType": "org.hibernate.exception.ConstraintViolationException",
  "rootCause": "org.postgresql.util.PSQLException: Unique constraint violation",
  "stackTrace": [
    "com.ecommerce.customer.service.CustomerService.create(CustomerService.java:45)",
    "com.ecommerce.common.exception.GlobalExceptionHandler.handle(GlobalExceptionHandler.java:78)",
    "..."
  ],
  "duration": 1250,
  "contextData": {
    "method": "POST",
    "path": "/api/customers",
    "remoteAddr": "192.168.1.100"
  }
}
```

## Using LogContext in Code

### Initializing Context

Context is automatically initialized by `LogContextFilter` for HTTP requests. No manual initialization needed.

### Setting User Information

```java
import com.ecommerce.common.logging.LogContextHolder;

// Set user ID when available (e.g., in authentication)
LogContextHolder.setUserId("user-123");

// Set customer ID for customer-related operations
LogContextHolder.setCustomerId("cust-456");

// Set component name for service methods
LogContextHolder.setComponentName("CustomerService");
```

### Adding Metadata

```java
// Add any relevant metadata for the operation
LogContextHolder.addMetadata("action", "create");
LogContextHolder.addMetadata("itemCount", 5);
LogContextHolder.addMetadata("totalAmount", 199.99);
```

### Accessing Context

```java
// Get current correlation ID
String correlationId = LogContextHolder.getCorrelationId();

// Get current request ID
String requestId = LogContextHolder.getRequestId();

// Get user ID
String userId = LogContextHolder.getUserId();

// Get full context
LogContext context = LogContextHolder.getContext();
```

## Logging Best Practices

### What to Log

✅ **Log:**
- API requests/responses (high level, not full body for large payloads)
- Important business events (order placed, payment processed)
- Warnings about unusual conditions (long query times, retry attempts)
- Errors and exceptions with full context
- Performance metrics (operation duration, query count)

❌ **Don't Log:**
- Passwords, API keys, tokens
- Credit card numbers or full PII
- Full request/response bodies (extract relevant fields)
- Debug variables in production logs
- Sensitive business data (unless redacted)

### Log Levels

- **DEBUG**: Development only - detailed diagnostic information
- **INFO**: Important business events and state changes
- **WARN**: Unusual conditions that may need attention
- **ERROR**: Application failures and exceptions
- **FATAL**: Critical system failures (rarely used)

### Logging at Different Levels

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomerService {
    
    public void createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer with email: {}", request.getEmail());
        
        try {
            // Business logic
            log.debug("Customer validation passed");
            
            Customer customer = customerRepository.save(new Customer());
            
            log.info("Customer created successfully - ID: {}", customer.getId());
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid customer data provided: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error creating customer", e);
            throw new CustomerCreationException("Failed to create customer", e);
        }
    }
}
```

## Searching Logs in Kibana

### By Correlation ID

Find all logs for a specific request:

```
correlationId: "550e8400-e29b-41d4-a716-446655440000"
```

### By User

Find all logs for a specific user:

```
userId: "user-123"
```

### By Service

Find all logs for a specific service:

```
serviceName: "customer-service"
```

### By Error Type

Find all occurrences of specific errors:

```
exceptionType: "org.hibernate.exception.ConstraintViolationException"
```

### Complex Queries

Find failed orders by customer:

```
serviceName: "order-service" AND level: "ERROR" AND customerId: "cust-456"
```

Find slow operations:

```
duration: [1000 TO *]
```

## Sensitive Data Redaction

Logstash automatically redacts:
- Passwords: `password=***REDACTED***`
- API tokens: `token=***REDACTED***`
- Credit cards: `credit_card=***REDACTED***`
- Authorization headers: `authorization=***REDACTED***`

### Custom Redaction

Add custom fields to redact in `monitoring/logstash/logstash.conf`:

```
mutate {
  gsub => [
    "message", "mySecret[\"']?\s*[:=]\s*[\"']?[^\"'\s,}]+", "mySecret=***REDACTED***"
  ]
}
```

## Performance Considerations

### Async Logging

Logback is configured with async appenders by default for better performance.

### Buffer Tuning

Configure in `application.yml`:

```yaml
logging:
  level:
    root: INFO
  config: classpath:logback-spring.xml
```

### Network Logging (Logstash)

For high-volume logging, configure Logstash TCP appender buffer:

```xml
<appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
  <queueSize>512</queueSize>
  <discardingThreshold>0</discardingThreshold>
</appender>
```

## Production Configuration

### Environment Variables

```bash
# Logstash server location
export LOGSTASH_HOST=logstash.example.com
export LOGSTASH_PORT=5000

# Elasticsearch location (for ILM policies)
export ELASTICSEARCH_HOST=elasticsearch.example.com
export ELASTICSEARCH_PORT=9200
```

### Active Profiles

```bash
# Production profile
java -jar app.jar --spring.profiles.active=prod
```

## Troubleshooting

### Logs Not Appearing in Elasticsearch

1. Check Logstash is running: `curl http://localhost:9600/_node/stats`
2. Check application connectivity: `telnet logstash-host 5000`
3. Review Logstash logs: `docker logs logstash-container`
4. Verify Elasticsearch index: `curl http://localhost:9200/_cat/indices`

### Performance Impact

If logging causes performance issues:

1. Reduce log level in production (WARN or ERROR)
2. Disable SQL logging (set hibernate level to WARN)
3. Increase Logstash buffer size
4. Check network connectivity to Logstash

### Missing Correlation ID

1. Verify `LogContextFilter` is registered as Spring bean
2. Check if endpoint is in filter exclusion list
3. Verify MDC is populated: Add debug log with `${correlationId}`

## Migration from Old Logging

### Before (Old Style)

```java
log.info("Customer created: " + customer.getId() + " by user: " + userId);
```

### After (Structured Logging)

```java
log.info("Customer created", 
  "customerId", customer.getId(),
  "userId", LogContextHolder.getUserId());
```

## Related Documentation

- [Database Metrics Guide](DATABASE_METRICS_GUIDE.md)
- [Query Optimization Guide](QUERY_OPTIMIZATION_GUIDE.md)
- [Monitoring Guide](MONITORING_GUIDE.md)
