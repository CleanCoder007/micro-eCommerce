# Comprehensive Monitoring Guide

## Overview

This guide covers the complete monitoring stack for the micro-ecommerce platform, including metrics collection, log aggregation, alerting, and dashboards.

## Monitoring Stack

### Components

1. **Prometheus** - Metrics collection and storage
2. **Grafana** - Metrics visualization and dashboards
3. **Logstash** - Log aggregation and processing
4. **Elasticsearch** - Log storage and search
5. **Kibana** - Log visualization and analysis
6. **Alertmanager** - Alert routing and management

## Metrics Monitoring

### Prometheus Configuration

Prometheus scrapes metrics from all services on `GET /actuator/prometheus`

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'customer-service'
    static_configs:
      - targets: ['localhost:8081']
        
  - job_name: 'order-service'
    static_configs:
      - targets: ['localhost:8082']
```

### Available Metrics

#### Application Metrics

- **JVM Metrics**
  - `jvm.memory.used` - Heap/non-heap memory usage
  - `jvm.gc.pause` - Garbage collection pause time
  - `jvm.threads.live` - Active thread count

- **HTTP Metrics**
  - `http.server.requests` - Request count and latency
  - `http.server.requests.total` - Total requests
  - `http.client.requests` - Outbound HTTP calls

- **Cache Metrics**
  - `cache.hits` - Cache hit count
  - `cache.misses` - Cache miss count
  - `cache.evictions` - Items evicted from cache

#### Database Metrics

- `db.query.duration` - Query execution time (p50, p95, p99)
- `db.query.count` - Total queries executed
- `db.query.slow` - Slow queries (>1000ms)
- `db.query.errors` - Failed queries
- `db.query.n_plus_one` - N+1 query patterns
- `db.connection.pool.active` - Active connections
- `db.connection.pool.idle` - Idle connections
- `db.connection.pool.total` - Total pool size
- `db.connection.pool.pending` - Waiting threads

See [Database Metrics Guide](DATABASE_METRICS_GUIDE.md) for detailed information.

### Grafana Dashboards

#### Pre-built Dashboards

1. **Database Performance Dashboard**
   - Location: `monitoring/grafana/dashboards/database-performance-dashboard.json`
   - Panels: Query time distribution, connection pool, slow queries
   - Use: Monitor database health and identify bottlenecks

2. **Service Health Dashboard** (TODO)
   - JVM metrics, request rates, error rates
   - P95/P99 latency by service
   - Dependency health

3. **Infrastructure Dashboard** (TODO)
   - CPU, memory, disk usage
   - Network I/O
   - Container resource usage

#### Accessing Grafana

1. Open `http://localhost:3000`
2. Default credentials: `admin:admin`
3. Go to Dashboards → Database Performance
4. Filter by service using template variables

#### Creating Custom Dashboards

1. Click "+" → Dashboard
2. Click "Add panel"
3. Select data source: Prometheus
4. Write queries (see Prometheus Query Examples below)
5. Save dashboard

### Prometheus Queries

#### Query Execution Time

```promql
# p95 latency last 5 minutes
histogram_quantile(0.95, rate(db_query_duration_seconds_bucket[5m])) * 1000

# p99 latency by service
histogram_quantile(0.99, rate(db_query_duration_seconds_bucket{service="customer-service"}[5m])) * 1000
```

#### Connection Pool Health

```promql
# Pool utilization percentage
(db_connection_pool_active / db_connection_pool_total) * 100

# Active connections trend
db_connection_pool_active
```

#### Query Performance

```promql
# Queries per second
sum(rate(db_query_count_total[5m]))

# Error rate
rate(db_query_errors_total[5m]) / rate(db_query_count_total[5m])

# Slow query rate (per minute)
rate(db_query_slow_total[1m]) * 60
```

#### HTTP Request Metrics

```promql
# Request latency p95
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) * 1000

# Request error rate by service
sum(rate(http_server_requests_seconds_bucket{status=~"5.."}[5m])) by (service)
```

## Log Monitoring

### Logstash Pipeline

Location: `monitoring/logstash/logstash.conf`

**Input:**
- TCP port 5000 (from applications)
- File input from `/var/log/ecommerce/`

**Processing:**
- JSON parsing
- Sensitive data redaction (passwords, tokens, credit cards)
- Service/environment enrichment
- Error severity classification

**Output:**
- Elasticsearch indices: `ecommerce-logs-YYYY.MM.DD`
- Separate index for critical errors: `ecommerce-critical-logs-YYYY.MM.DD`

### Elasticsearch Index Management

**Index Template**: `monitoring/elasticsearch/templates/ecommerce-logs-template.json`

**ILM Policy**: `monitoring/elasticsearch/policies/ilm-ecommerce-logs-policy.json`

**Retention:**
- Hot phase: Current day (1-50GB per shard)
- Warm phase: 3-15 days (shrink and merge)
- Cold phase: 15-30 days (searchable snapshot)
- Delete: > 30 days

### Kibana Log Search

#### By Correlation ID

Find all logs for a single request:

```
correlationId: "550e8400-e29b-41d4-a716-446655440000"
```

#### By Error Type

```
level: "ERROR" AND exceptionType: "NullPointerException"
```

#### By Service and User

```
serviceName: "order-service" AND userId: "user-123"
```

#### Complex Queries

Failed orders in last hour:

```
serviceName: "order-service" AND level: "ERROR" AND @timestamp: [now-1h TO now]
```

Slow database operations:

```
serviceName: "customer-service" AND duration: [500 TO *]
```

See [Structured Logging Guide](STRUCTURED_LOGGING_GUIDE.md) for complete logging documentation.

## Alerting

### Alert Rules

Prometheus alert rules: `monitoring/prometheus/rules/database-alerts.yml`

#### Database Alerts

1. **SlowQuerySpike** (Warning)
   - Condition: 10x above baseline
   - Duration: 2 minutes
   - Action: Investigate slow queries

2. **HighSlowQueryRate** (Critical)
   - Condition: >10 slow queries/minute
   - Duration: 5 minutes
   - Action: Page on-call engineer

3. **ConnectionPoolExhaustion** (Warning)
   - Condition: >80% utilization
   - Duration: 2 minutes
   - Action: Review connection usage

4. **ConnectionPoolCritical** (Critical)
   - Condition: >95% utilization
   - Duration: 1 minute
   - Action: Immediate scaling needed

5. **HighQueryErrorRate** (Critical)
   - Condition: >5% error rate
   - Duration: 3 minutes
   - Action: Check database logs

#### HTTP Alerts

- **HighErrorRate**: >1% error rate for 5 minutes
- **HighLatency**: p95 latency > 1 second for 5 minutes
- **ServiceDown**: No metrics for 2 minutes

### Alertmanager Configuration

```yaml
# alertmanager.yml
route:
  group_by: ['service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'default'
  
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true
      
    - match:
        severity: warning
      receiver: 'slack'

receivers:
  - name: 'default'
    # No receiver (internal only)
    
  - name: 'slack'
    slack_configs:
      - api_url: '<YOUR_SLACK_WEBHOOK>'
        channel: '#alerts'
        
  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: '<YOUR_PAGERDUTY_KEY>'
```

## SLOs and SLIs

### Service Level Objectives (SLOs)

#### Availability SLO: 99.9%

**SLI**: Successful requests / Total requests

```promql
sum(rate(http_server_requests_seconds_bucket{status=~"2.."}[5m])) / 
sum(rate(http_server_requests_seconds_bucket[5m]))
```

#### Latency SLO: p95 < 200ms

**SLI**: p95 request latency

```promql
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) * 1000
```

#### Database SLO: p95 query < 100ms

**SLI**: p95 query execution time

```promql
histogram_quantile(0.95, rate(db_query_duration_seconds_bucket[5m])) * 1000
```

### Error Budget

- Monthly error budget: 0.1% downtime = 43.2 minutes
- Once error budget exhausted, focus on reliability over features

## Deployment Monitoring

### Health Checks

All services expose health endpoints:

```
GET /actuator/health
```

Response example:

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "redis": {
      "status": "UP"
    }
  }
}
```

### Liveness and Readiness

- **Liveness** (/actuator/health/liveness): Is the service running?
- **Readiness** (/actuator/health/readiness): Can the service accept traffic?

Used by Kubernetes for pod lifecycle management.

### Canary Deployments

Monitor key metrics during new deployments:

1. Error rate < 0.1% increase
2. p95 latency < 10% increase
3. No database connection pool exhaustion
4. No N+1 query patterns

## Troubleshooting

### High CPU Usage

1. Check JVM metrics: `jvm.process.cpu.usage`
2. Review query logs for CPU-intensive operations
3. Check for thread contention: `jvm.threads.peak`
4. Consider query optimization

### High Memory Usage

1. Monitor heap usage: `jvm.memory.used{area="heap"}`
2. Check garbage collection: `jvm.gc.pause`
3. Review cache hit rates
4. Look for memory leaks in application logs

### Database Connection Issues

1. Check pool metrics: `db.connection.pool.active/total`
2. Review slow query logs
3. Check for connection leaks in code
4. Increase pool size or reduce query time

### High Error Rate

1. Search Kibana by `level: "ERROR"`
2. Group by error type: `exceptionType`
3. Identify affected services: `serviceName`
4. Review error trends over time

## Best Practices

### Metric Collection

- Collect metrics from all services uniformly
- Use consistent metric names and labels
- Retain metrics for 30+ days (Prometheus)
- Archive old metrics to object storage

### Log Management

- Use structured JSON logging
- Include correlation ID in all logs
- Redact sensitive data automatically
- Retain logs for compliance (30-90 days)

### Dashboard Maintenance

- Review dashboards monthly
- Remove unused panels
- Keep dashboards focused (1 service per dashboard)
- Version control dashboard JSON

### Alert Tuning

- Tune thresholds based on baseline behavior
- Avoid alert fatigue (< 5 alerts per day on average)
- Automate resolution where possible
- Review alert response times weekly

## Related Documentation

- [Database Metrics Guide](DATABASE_METRICS_GUIDE.md)
- [Structured Logging Guide](STRUCTURED_LOGGING_GUIDE.md)
- [Query Optimization Guide](QUERY_OPTIMIZATION_GUIDE.md)
