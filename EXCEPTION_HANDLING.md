# Exception Handling & Resilience Configuration - Phase 1

## Overview

Phase 1 implements comprehensive exception handling and resilience patterns across the e-commerce microservices architecture using:
- **Kafka Dead Letter Topics (DLT)** - for failed message processing
- **Circuit Breakers** - to prevent cascading failures
- **Exponential Backoff & Retry** - for transient error recovery
- **Time Limiters** - to prevent hanging requests
- **Enhanced ControllerAdvice** - for standardized error responses

## Architecture

### Kafka Dead Letter Topic (DLT) Strategy

When a Kafka consumer fails to process a message after retries, the message is automatically routed to a Dead Letter Topic for later investigation and recovery.

#### DLT Naming Convention
```
<service>-events.DLT  (main DLT for all events from a service)

Examples:
- order-events.DLT
- inventory-events.DLT
- payment-events.DLT
- customer-events.DLT
```

#### How DLT Works

1. **Message Processing Fails** → Message stays in the main topic's error handler
2. **Retries Exhausted** → DefaultErrorHandler routes message to DLT
3. **Manual Review** → Operations team monitors DLT topics
4. **Recovery** → Message can be replayed from DLT after fix

#### DLT Configuration
- **Partitions**: 1 (serialized processing)
- **Retention**: Indefinite (for investigation)
- **Monitoring**: Prometheus metrics track DLT size and lag

### Circuit Breaker States & Transitions

```
CLOSED (Normal)
  ↓ (5 failures in 10 requests)
OPEN (Rejecting calls)
  ↓ (after 10s wait)
HALF_OPEN (Testing)
  ↓ (3 success calls)
CLOSED (Recovered)
```

#### Configuration Thresholds
- **Sliding Window**: Last 10 requests
- **Failure Threshold**: 50% failure rate
- **Minimum Calls**: 5 (before calculation)
- **Open Duration**: 10s (wait before trying again)
- **Half-Open Calls**: 3 (test requests before deciding)

### Exponential Backoff Retry Strategy

#### Timing Schedule
```
Retry 1: 100ms  (initial delay)
Retry 2: 200ms  (100ms × 2)
Retry 3: 400ms  (200ms × 2)
Max:     5000ms (capped at 5s)

Total max wait before giving up: ~700ms + circuit breaker wait
```

#### Retry Conditions
**Retryable Errors:**
- `ConnectException` - Network connection failed
- `SocketTimeoutException` - Read timeout
- `IOException` - Transient I/O errors
- Kafka producer timeouts

**Non-Retryable Errors:**
- `ValidationException` (400 Bad Request)
- `HttpClientErrorException` (4xx responses)
- `BusinessException`

## Exception Hierarchy

### Common Exceptions (all services)

```
BaseException (RuntimeException)
├── CircuitBreakerException (503)
├── TimeLimitExceededException (504)
├── RetryableException (429)
├── DeadLetterException (500)
├── BusinessException (400)
├── ValidationException (400)
├── ResourceNotFoundException (404)
└── EventPublishingException (500)
```

### HTTP Status Codes

| Exception | Status | Meaning |
|-----------|--------|---------|
| CircuitBreakerException | 503 | Service temporarily unavailable |
| CallNotPermittedException | 503 | Circuit breaker open (same as above) |
| TimeLimitExceededException | 504 | Operation exceeded timeout |
| RequestTimeoutException | 504 | Request took too long |
| RetryableException | 429 | Too many retries in progress |
| DeadLetterException | 500 | Message failed and sent to DLT |
| ValidationException | 400 | Input validation failed |
| BusinessException | 400 | Business rule violation |
| ResourceNotFoundException | 404 | Resource not found |

## Service-Specific Configuration

### Order Service (8083)

**Event Listeners** (with resilience):
- `handlePaymentProcessed` - Process successful payment
- `handleInventoryFailed` - Handle inventory reservation failure
- `handlePaymentFailed` - Handle payment processing failure
- `handleRefundCompleted` - Confirm refund completion

**Resilience Config**:
- Circuit Breaker: 5 failures in 10 requests → OPEN for 10s
- Retry: 3 attempts, 100ms initial, 2x backoff
- Time Limit: 5s per operation, 3s for Kafka

### Inventory Service (8082)

**Event Listeners** (with resilience):
- `handleOrderCreated` - Reserve inventory for order
- `handlePaymentFailed` - Release reserved inventory (compensation)

**Resilience Config**:
- Same as Order Service
- Optimized for quick inventory checks

### Payment Service (8084)

**Event Listeners** (with resilience):
- `handleInventoryReserved` - Process payment
- `handleOrderCancelled` - Refund payment (compensation)

**Resilience Config**:
- Circuit Breaker: 5 failures in 10 requests → OPEN
- Retry: 3 attempts, exponential backoff
- Time Limit: 5s, with 3s Kafka timeout

### Customer Service (8081)

**Resilience Config** (no Kafka events):
- REST client calls decorated with circuit breaker
- 3 retries with exponential backoff
- 10s timeout on external calls

## Error Response Format

### Standard Error Response
```json
{
  "status": 503,
  "error": "SERVICE_UNAVAILABLE",
  "message": "Circuit breaker open for kafkaPublisher",
  "errorCode": "CIRCUIT_BREAKER_OPEN",
  "timestamp": "2026-09-28T10:30:45.123456",
  "path": "/api/orders"
}
```

### Validation Error Response
```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-09-28T10:30:45.123456",
  "path": "/api/orders",
  "errors": {
    "amount": "Must be greater than 0",
    "orderId": "Must not be blank"
  }
}
```

## Failure Scenario Examples

### Scenario 1: Kafka Broker Down

**Timeline:**
1. Order Service tries to publish event
2. First attempt fails (CONNECT_TIMEOUT)
3. Retry 1 @ 100ms → fails
4. Retry 2 @ 200ms → fails
5. Retry 3 @ 400ms → fails
6. Message sent to Dead Letter Topic
7. API returns 500 (DEAD_LETTER_EXCEPTION)
8. Operations team alerted by DLT monitoring

**Recovery:**
- Kafka broker comes online
- DLT consumer replays messages
- Services process recovered messages

### Scenario 2: External API Slow (Payment Gateway)

**Timeline:**
1. Payment Service calls payment gateway
2. Call takes 8 seconds (timeout: 5s)
3. Request times out (RequestTimeoutException)
4. Circuitbreaker records failure
5. After 5 failures in 10 requests: OPEN
6. New requests immediately rejected (CallNotPermittedException)
7. API returns 503 (CIRCUIT_BREAKER_OPEN)

**Recovery:**
- Payment gateway performance improves
- Circuit breaker waits 10s then enters HALF_OPEN
- Next 3 requests are test calls
- If all 3 succeed → circuit returns to CLOSED
- Normal traffic resumes

### Scenario 3: Database Connection Pool Exhausted

**Timeline:**
1. Inventory Service queries database
2. All connections busy (timeout)
3. IOException thrown
4. Retry 1 @ 100ms → connections still busy
5. Retry 2 @ 200ms → connections freed
6. Retry succeeds
7. Event processed successfully

### Scenario 4: Validation Error (Non-Retryable)

**Timeline:**
1. Client sends order with missing productId
2. Service validation fails
3. ValidationException thrown (NOT retried)
4. API returns 400 (BAD_REQUEST) immediately

**Note:** Validation errors are not retried because they won't succeed on retry

## Monitoring & Alerting

### Prometheus Metrics

**Circuit Breaker Metrics:**
```
resilience4j_circuitbreaker_state[cb_name, instance]
resilience4j_circuitbreaker_calls_total[cb_name, state]
resilience4j_circuitbreaker_failure_rate[cb_name]
```

**Retry Metrics:**
```
resilience4j_retry_calls_total[retry_name, outcome]
resilience4j_retry_attempts_total[retry_name]
```

**Kafka DLT Metrics:**
```
kafka_consumer_lag_sum[topic=*-events.DLT, group_id]
kafka_topic_message_count[topic=*-events.DLT]
```

### Alert Conditions

1. **Circuit Breaker Open for >5min**
   - Indicates persistent failure
   - Check target service health

2. **DLT Lag Increasing**
   - Messages accumulating in DLT
   - Consumer may be offline

3. **Retry Attempts >threshold**
   - Too many transient failures
   - Check network connectivity

## Testing

### Unit Tests
- Circuit breaker state transitions
- Exponential backoff timing verification
- Exception mapping to HTTP status codes

### Integration Tests
- End-to-end saga with failures
- DLT routing on consumer failure
- Circuit breaker recovery process
- Retry with eventual success

### Load Tests
- Sustained failures trigger circuit breaker
- Recovery time measurement
- DLT throughput capacity

## Deployment Checklist

- [ ] All services built successfully
- [ ] Tests pass with 80%+ coverage
- [ ] DLT topics created in Kafka
- [ ] Prometheus scraping resilience4j metrics
- [ ] Circuit breaker monitoring dashboard created
- [ ] DLT monitoring alerts configured
- [ ] Documentation deployed
- [ ] Team trained on troubleshooting DLT
- [ ] Runbook for circuit breaker recovery created

## Related Documentation

- [RESILIENCE4J_CONFIG.md](./RESILIENCE4J_CONFIG.md) - Detailed configuration reference
- [SAGA_PATTERN_GUIDE.md](./SAGA_PATTERN_GUIDE.md) - Saga orchestration details
- [TESTING_GUIDE.md](./TESTING_GUIDE.md) - Test scenarios and procedures
