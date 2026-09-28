# Resilience4j Configuration Reference

## Overview

Resilience4j provides the following patterns for building fault-tolerant microservices:

- **Circuit Breaker** - Prevents calls to failing services
- **Retry** - Retries failed calls with backoff
- **Time Limiter** - Enforces timeout on operations
- **Rate Limiter** - Limits request rate (available but not used in Phase 1)
- **Bulkhead** - Isolates thread pools (available but not used in Phase 1)

## File Locations

### Configuration Files
- Root POM: `pom.xml` - Resilience4j dependencies
- Order Service: `services/order-service/src/main/resources/application.yml`
- Inventory Service: `services/inventory-service/src/main/resources/application.yml`
- Payment Service: `services/payment-service/src/main/resources/application.yml`
- Customer Service: `services/customer-service/src/main/resources/application.yml`

### Common Configuration
- `common/src/main/java/com/ecommerce/common/config/KafkaErrorHandlingConfig.java`
- `common/src/main/java/com/ecommerce/common/config/KafkaEventConfig.java`

### Exception Classes
- `common/src/main/java/com/ecommerce/common/exception/CircuitBreakerException.java`
- `common/src/main/java/com/ecommerce/common/exception/RetryableException.java`
- `common/src/main/java/com/ecommerce/common/exception/DeadLetterException.java`
- `common/src/main/java/com/ecommerce/common/exception/TimeLimitExceededException.java`

## Detailed Configuration

### Circuit Breaker

#### Purpose
Prevent cascading failures by stopping calls to failing services

#### Configuration Parameters

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true              # Expose health check
        slidingWindowSize: 10                       # Track last 10 calls
        minimumNumberOfCalls: 5                     # Need 5 calls before deciding
        permittedNumberOfCallsInHalfOpenState: 3    # Allow 3 test calls
        automaticTransitionFromOpenToHalfOpenEnabled: true  # Auto try again
        waitDurationInOpenState: 10s                # Wait 10s before retry
        failureRateThreshold: 50                    # Open if >50% fail
        slowCallRateThreshold: 100                  # Open if >100% slow
        slowCallDurationThreshold: 2s               # Calls >2s = slow
        recordExceptions:                           # Track these errors
          - java.net.ConnectException
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignoreExceptions:                           # Don't count these
          - com.ecommerce.common.exception.ValidationException
```

#### State Diagram

```
CLOSED (Initial)
  │ slidingWindowSize = 10 calls
  │ failureRateThreshold = 50%
  └─→ (5+ failures in 10 calls)
       │
       ↓
OPEN (Rejecting)
  │ waitDurationInOpenState = 10s
  │ All new calls immediately fail
  └─→ (after 10s elapsed)
       │
       ↓
HALF_OPEN (Testing)
  │ permittedNumberOfCallsInHalfOpenState = 3
  │ Allow 3 test calls through
  │
  ├─→ (all 3 succeed) → CLOSED
  │
  └─→ (any fails) → OPEN (restart wait timer)
```

#### Instance Configuration Examples

**For Kafka Publishing** (most failures expected):
```yaml
kafkaPublisher:
  baseConfig: default
  slidingWindowSize: 5              # Smaller window
  failureRateThreshold: 50
  waitDurationInOpenState: 30s      # Longer wait for Kafka
```

**For REST Clients** (external APIs):
```yaml
restClient:
  baseConfig: default
  slidingWindowSize: 10
  failureRateThreshold: 50
  waitDurationInOpenState: 30s      # Give external API time to recover
```

### Retry

#### Purpose
Automatically retry failed operations with exponential backoff

#### Configuration Parameters

```yaml
resilience4j:
  retry:
    configs:
      default:
        maxAttempts: 3                           # Try 3 times total
        waitDuration: 100ms                      # Initial wait
        intervalFunction: exponential             # Backoff strategy
        exponentialBackoffMultiplier: 2.0        # 100ms, 200ms, 400ms
        retryExceptions:                         # Retry these
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - java.io.IOException
        ignoreExceptions:                        # Don't retry these
          - com.ecommerce.common.exception.ValidationException
          - org.springframework.web.client.HttpClientErrorException
```

#### Backoff Formula

```
Wait(attempt) = initialDelay * (multiplier ^ (attempt - 1))
Cap at maxDelay

Example (exponential, multiplier=2):
Attempt 1: fail immediately
Attempt 2: wait 100ms, then retry
Attempt 3: wait 200ms, then retry  
Attempt 4: wait 400ms, then retry (hits max 5000ms)
After Attempt 4 fails → Circuit Breaker decides
```

#### Instance Configuration Examples

**For Kafka** (short timeout, quick failure detection):
```yaml
kafkaPublisher:
  baseConfig: default
  maxAttempts: 3
  waitDuration: 100ms
  exponentialBackoffMultiplier: 2.0
```

**For REST** (longer timeout allowed):
```yaml
restClient:
  baseConfig: default
  maxAttempts: 3
  waitDuration: 200ms              # Start with 200ms
  exponentialBackoffMultiplier: 2.0
```

### Time Limiter

#### Purpose
Enforce maximum operation duration to prevent hanging

#### Configuration Parameters

```yaml
resilience4j:
  timelimiter:
    configs:
      default:
        cancelRunningFuture: false    # Don't cancel if timeout hits
        timeoutDuration: 5s           # Max 5 seconds
```

#### Instance Configuration Examples

**For Kafka Operations** (fast publish expected):
```yaml
kafkaPublisher:
  baseConfig: default
  timeoutDuration: 3s               # 3 second max for Kafka publish
```

**For Database Operations** (can be slower):
```yaml
databaseOperation:
  baseConfig: default
  timeoutDuration: 5s               # 5 second max for DB query
```

**For REST Calls** (external API, variable latency):
```yaml
restClient:
  baseConfig: default
  timeoutDuration: 10s              # 10 second max for external API
```

## Decorator Usage in Code

### Single Decorator

```java
@CircuitBreaker(name = "orderService")
public void handleEvent(Event event) {
  // method implementation
}
```

### Multiple Decorators (Recommended for Kafka)

```java
@CircuitBreaker(name = "kafkaPublisher")
@Retry(name = "kafkaPublisher")
@TimeLimiter(name = "kafkaPublisher")
public void handleEvent(Event event) {
  // method implementation
  // Decorators applied in order: TimeLimiter → Retry → CircuitBreaker
}
```

#### Execution Order

```
External Call
  ↓
TimeLimiter (enforce timeout)
  ↓
Retry (with backoff)
  ├─→ Attempt 1 → CircuitBreaker
  ├─→ Attempt 2 → CircuitBreaker (after wait)
  └─→ Attempt 3 → CircuitBreaker (after wait)
  ↓
Return Result or Exception
```

### Fallback Method

```java
@CircuitBreaker(name = "orderService", fallbackMethod = "fallbackHandleEvent")
public void handleEvent(Event event, Acknowledgment ack) {
  // Normal processing
}

public void fallbackHandleEvent(Event event, Acknowledgment ack, Exception ex) {
  log.error("Fallback triggered: {}", ex.getMessage());
  // Handle failure gracefully
}
```

#### Fallback Rules
- Fallback method must have **same parameters** + `Exception ex`
- Fallback method must have **same return type** (void for event handlers)
- Must be in same class
- Called when circuit is OPEN or max retries exceeded

## Health Checks

### Spring Boot Actuator Integration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  endpoint:
    health:
      show-details: always
```

### Health Endpoint Response

```
GET /actuator/health

{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "orderService": {
          "status": "UP",
          "details": {
            "state": "CLOSED"
          }
        },
        "kafkaPublisher": {
          "status": "DOWN",
          "details": {
            "state": "OPEN",
            "failureRate": 60.0,
            "waitDurationInOpenState": "PT30S"
          }
        }
      }
    }
  }
}
```

## Metrics and Monitoring

### Prometheus Metrics Exposed

#### Circuit Breaker
```
resilience4j_circuitbreaker_state{name="orderService"} 0  # CLOSED
resilience4j_circuitbreaker_state{name="kafkaPublisher"} 1  # OPEN
resilience4j_circuitbreaker_failure_rate{name="orderService"} 0.25
resilience4j_circuitbreaker_calls_total{name="orderService",kind="successful"} 150
resilience4j_circuitbreaker_calls_total{name="orderService",kind="failed"} 10
```

#### Retry
```
resilience4j_retry_calls_total{name="orderService",outcome="success"} 145
resilience4j_retry_calls_total{name="orderService",outcome="retry"} 8
resilience4j_retry_calls_total{name="orderService",outcome="failed"} 5
resilience4j_retry_attempts_total{name="orderService",outcome="success"} 153
```

#### Time Limiter
```
resilience4j_timelimiter_calls_total{name="kafkaPublisher",outcome="success"} 980
resilience4j_timelimiter_calls_total{name="kafkaPublisher",outcome="timeout"} 3
resilience4j_timelimiter_calls_total{name="kafkaPublisher",outcome="failed"} 17
```

### Grafana Dashboards

Suggested panels:
1. **Circuit Breaker State History** - Track state changes over time
2. **Failure Rate by Service** - Identify problem services
3. **Retry Attempts Distribution** - Monitor backoff effectiveness
4. **Time Limit Violations** - Track timeout occurrences
5. **DLT Message Count** - Monitor failed message accumulation

## Common Configuration Patterns

### Pattern 1: Conservative (Low Error Tolerance)
```yaml
circuitbreaker:
  slidingWindowSize: 10
  minimumNumberOfCalls: 3
  failureRateThreshold: 30
  waitDurationInOpenState: 5s
```
**Use for:** Critical services like payment

### Pattern 2: Balanced (Default)
```yaml
circuitbreaker:
  slidingWindowSize: 10
  minimumNumberOfCalls: 5
  failureRateThreshold: 50
  waitDurationInOpenState: 10s
```
**Use for:** Regular services like order, inventory

### Pattern 3: Tolerant (High Error Tolerance)
```yaml
circuitbreaker:
  slidingWindowSize: 20
  minimumNumberOfCalls: 10
  failureRateThreshold: 70
  waitDurationInOpenState: 30s
```
**Use for:** External APIs with variable reliability

## Troubleshooting

### Circuit Breaker Stuck in OPEN State

**Symptom:** Service returns 503 for all requests

**Cause:** 
- Service was genuinely down but still is
- Wait duration expired but half-open test failed
- Fast failure before recovery

**Fix:**
1. Check target service health: `curl -s http://target:port/actuator/health`
2. If healthy, wait for `waitDurationInOpenState` to pass
3. If not recovered, may need manual intervention
4. Monitor `/actuator/health` for state transitions

### Too Many Retries

**Symptom:** High latency, many timeout errors

**Cause:**
- Backoff multiplier too low (retries too fast)
- Max attempts too high
- Service slow but not dead (circuit not triggering)

**Fix:**
1. Increase `waitDuration` and `exponentialBackoffMultiplier`
2. Decrease `maxAttempts` if retries ineffective
3. Lower `slowCallDurationThreshold` to trip circuit faster
4. Check network latency with `ping` or `tc` commands

### DLT Accumulating Messages

**Symptom:** DLT topic grows but no processing

**Cause:**
- Message format changed
- Service consumer offline
- Application bug in consumer

**Fix:**
1. Check DLT consumer group: `kafka-consumer-groups.sh --describe`
2. Review recent service deployments
3. Check logs: `kubectl logs -f deployment/service-name`
4. Consider replaying messages after fix

## Related Files

- `common/pom.xml` - Resilience4j BOM and dependencies
- `services/*/application.yml` - Per-service configuration
- `services/*/exception/GlobalExceptionHandler.java` - Exception mapping
- `EXCEPTION_HANDLING.md` - Business logic and scenarios
