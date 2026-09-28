# Health Checks and Monitoring Guide

## Overview

The health check system provides real-time insights into service dependencies (database, Redis, Kafka). Custom health indicators monitor connectivity and response times, enabling Kubernetes probes and monitoring dashboards.

## Health Indicators

### Database Health Indicator

**Purpose**: Monitors database connectivity and performance

**Implementation**: 
- Executes a connection test from the connection pool
- Measures response time
- Reports degraded status if response time > 1000ms

**Status Values**:
- **UP**: Database is connected and responsive
- **DEGRADED**: Database is connected but slow (>1000ms)
- **DOWN**: Database connection failed

**Details**:
```json
{
  "status": "UP",
  "details": {
    "database": "Connected",
    "responseTime": "45ms"
  }
}
```

### Redis Health Indicator

**Purpose**: Monitors Redis cache connectivity

**Implementation**:
- Performs a SET/GET operation on a test key
- Measures response time
- Cleans up test key after check

**Status Values**:
- **UP**: Redis is connected and responsive
- **DEGRADED**: Redis is slow (>500ms)
- **DOWN**: Redis connection failed

**Details**:
```json
{
  "status": "UP",
  "details": {
    "redis": "Connected",
    "responseTime": "12ms"
  }
}
```

### Kafka Health Indicator

**Purpose**: Monitors Kafka broker connectivity

**Implementation**:
- Sends a test message to health-check-topic
- Waits for acknowledgment (5 second timeout)
- Measures response time

**Status Values**:
- **UP**: Kafka is connected and responsive
- **DEGRADED**: Kafka is slow (>1000ms)
- **DOWN**: Kafka broker unreachable

**Details**:
```json
{
  "status": "UP",
  "details": {
    "kafka": "Connected",
    "responseTime": "28ms"
  }
}
```

## Health Check Endpoints

### Liveness Probe

**Endpoint**: `GET /actuator/health/liveness`

**Purpose**: Check if service is running

**Response**:
```json
{
  "status": "UP"
}
```

**Kubernetes Config**:
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3
```

### Readiness Probe

**Endpoint**: `GET /actuator/health/readiness`

**Purpose**: Check if service can handle requests

**Checks**:
- Database connectivity
- Redis connectivity
- Kafka connectivity
- Disk space

**Response**:
```json
{
  "status": "UP",
  "components": {
    "databaseHealthIndicator": {"status": "UP"},
    "redisHealthIndicator": {"status": "UP"},
    "kafkaHealthIndicator": {"status": "UP"}
  }
}
```

**Kubernetes Config**:
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  failureThreshold: 3
```

### Full Health Information

**Endpoint**: `GET /actuator/health`

**Purpose**: Detailed health information for monitoring dashboards

**Response**:
```json
{
  "status": "UP",
  "components": {
    "databaseHealthIndicator": {
      "status": "UP",
      "details": {
        "database": "Connected",
        "responseTime": "45ms"
      }
    },
    "redisHealthIndicator": {
      "status": "UP",
      "details": {
        "redis": "Connected",
        "responseTime": "12ms"
      }
    },
    "kafkaHealthIndicator": {
      "status": "UP",
      "details": {
        "kafka": "Connected",
        "responseTime": "28ms"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 10737418240,
        "free": 5368709120,
        "threshold": 10485760
      }
    }
  }
}
```

## Configuration

### application.yml

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized
      probes:
        enabled: true
  health:
    defaults:
      enabled: true
    diskSpace:
      enabled: true
    db:
      enabled: true
    redis:
      enabled: true
    kafka:
      enabled: true
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

### Health Indicator Registration

Health indicators are auto-registered as Spring components:

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseHealthIndicator extends AbstractHealthIndicator {
    // Implementation
}
```

## Kubernetes Integration

### Deployment Configuration

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  template:
    spec:
      containers:
      - name: order-service
        image: order-service:latest
        ports:
        - containerPort: 8080
        
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          failureThreshold: 3
          timeoutSeconds: 2
        
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 5
          failureThreshold: 3
          timeoutSeconds: 2
        
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

### Service Configuration

```yaml
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
  selector:
    app: order-service
```

## Monitoring and Dashboards

### Prometheus Metrics

**Endpoint**: `GET /actuator/metrics/health.check.duration`

**Metrics Available**:
- `health.check.duration`: Time taken for health check
- `health.check.status`: Current health status (1=UP, 0=DOWN)
- `db.connection.active`: Active database connections
- `redis.commands`: Redis commands executed
- `kafka.messages`: Messages sent/received

### Grafana Dashboard Queries

**Database Health**:
```promql
health_check_duration_seconds_bucket{component="database"}
```

**Redis Health**:
```promql
health_check_duration_seconds_bucket{component="redis"}
```

**Kafka Health**:
```promql
health_check_duration_seconds_bucket{component="kafka"}
```

## Alerting Rules

### Prometheus AlertManager Configuration

```yaml
groups:
- name: health_checks
  rules:
  - alert: ServiceUnhealthy
    expr: health_status{component="database"} == 0
    for: 5m
    annotations:
      summary: "Database health check failed"
      action: "Check database connectivity"
  
  - alert: DegradedPerformance
    expr: health_check_duration_seconds{component="database"} > 1
    for: 10m
    annotations:
      summary: "Database response time is high"
      action: "Review database performance"
  
  - alert: KafkaDown
    expr: health_status{component="kafka"} == 0
    for: 2m
    annotations:
      summary: "Kafka broker is unreachable"
      action: "Check Kafka cluster status"
```

## Manual Health Checks

### Check Service Health

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

### Check Database Only

```bash
curl -s http://localhost:8080/actuator/health | jq '.components.databaseHealthIndicator'
```

### Check Redis Only

```bash
curl -s http://localhost:8080/actuator/health | jq '.components.redisHealthIndicator'
```

### Check Kafka Only

```bash
curl -s http://localhost:8080/actuator/health | jq '.components.kafkaHealthIndicator'
```

### Monitor Health Continuously

```bash
watch -n 5 'curl -s http://localhost:8080/actuator/health | jq'
```

## Troubleshooting

### Database Health Check Failing

1. Verify database is running
2. Check connection string in application.yml
3. Verify credentials and permissions
4. Review connection pool size
5. Check network connectivity to database host

### Redis Health Check Failing

1. Verify Redis is running
2. Check Redis connection string
3. Verify network connectivity
4. Check Redis memory usage
5. Review Redis logs

### Kafka Health Check Failing

1. Verify Kafka brokers are running
2. Check bootstrap-servers configuration
3. Verify health-check-topic exists or auto-create is enabled
4. Check network connectivity to Kafka
5. Review Kafka broker logs

### All Health Checks Returning DEGRADED

1. Check system resource usage (CPU, memory)
2. Review network latency
3. Check for high load on dependencies
4. Review application logs
5. Consider scaling services

## Best Practices

1. **Set appropriate timeouts** in Kubernetes probes
2. **Monitor health metrics** in Grafana dashboards
3. **Configure alerting** for health check failures
4. **Test probes** in staging before production
5. **Keep health indicator logic simple** to avoid false negatives
6. **Monitor health check duration** to catch performance issues
7. **Document recovery procedures** for each component
8. **Regularly review** health check configurations

## Related Components

- `DatabaseHealthIndicator`: Database connectivity check
- `RedisHealthIndicator`: Redis cache check
- `KafkaHealthIndicator`: Kafka broker check
- `RequestResponseLoggingFilter`: Request/response logging
- `ErrorNotificationPublisher`: Error event publishing
