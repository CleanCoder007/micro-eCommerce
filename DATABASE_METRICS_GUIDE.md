# Database Metrics Collection Guide

## Overview

This guide explains the database metrics collected by the micro-ecommerce platform using Micrometer and how to use them for monitoring and optimization.

## Metrics Collected

### 1. Query Execution Time

**Metric Name:** `db.query.duration`

Tracks the execution time of all database queries with percentile distribution.

- **Type:** Timer (histogram)
- **Unit:** Milliseconds
- **Tags:**
  - `service`: Repository class name
  - `method`: Repository method name
  - `outcome`: `success` or `failure`
- **Percentiles:** p50, p95, p99
- **Use Case:** Identify slow queries and latency trends

### 2. Query Count

**Metric Name:** `db.query.count`

Counts total database queries executed.

- **Type:** Counter
- **Tags:**
  - `service`: Repository class name
  - `method`: Repository method name
  - `outcome`: `success` or `failure`
- **Use Case:** Track query volume and success/failure ratio

### 3. Slow Queries

**Metric Name:** `db.query.slow`

Counts queries exceeding 1000ms execution time.

- **Type:** Counter
- **Threshold:** 1000ms
- **Tags:**
  - `service`: Repository class name
  - `method`: Repository method name
  - `outcome`: `success` or `failure`
- **Use Case:** Alert on performance degradation

### 4. Query Errors

**Metric Name:** `db.query.errors`

Counts failed queries due to exceptions.

- **Type:** Counter
- **Tags:**
  - `service`: Repository class name
  - `method`: Repository method name
- **Use Case:** Track database connectivity and query errors

### 5. Connection Pool Metrics

#### Active Connections
**Metric Name:** `db.connection.pool.active`
- **Type:** Gauge
- **Unit:** Number of active connections

#### Idle Connections
**Metric Name:** `db.connection.pool.idle`
- **Type:** Gauge
- **Unit:** Number of idle connections

#### Total Connections
**Metric Name:** `db.connection.pool.total`
- **Type:** Gauge
- **Unit:** Total pool size

#### Pending Threads
**Metric Name:** `db.connection.pool.pending`
- **Type:** Gauge
- **Unit:** Threads waiting for connection

**Use Case:** Monitor connection pool health and exhaustion

### 6. Query Operations

**Metric Name:** `db.query.operation`

Tracks INSERT, UPDATE, DELETE operations by entity type.

- **Type:** Counter
- **Tags:**
  - `operation`: INSERT, UPDATE, or DELETE
  - `entity`: Entity class name
- **Use Case:** Track data modification patterns

### 7. N+1 Query Detection

**Metric Name:** `db.query.n_plus_one`

Counts occurrences of potential N+1 query problems (>5 queries per request).

- **Type:** Counter
- **Tags:**
  - `entity`: Entity class name
- **Threshold:** 5 queries per request
- **Use Case:** Identify performance issues from inefficient queries

## Configuration

### Enable/Disable Metrics

Add to `application.yml`:

```yaml
metrics:
  database:
    enabled: true  # Default: true
```

### HikariCP Configuration

For optimal metrics, configure HikariCP in `application.yml`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-timeout: 20000
      leak-detection-threshold: 60000
```

### Hibernate Configuration (Development)

Enable SQL logging in `application-dev.yml`:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
    show-sql: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

## Prometheus Queries

### Query Response Time (p95)

```promql
histogram_quantile(0.95, rate(db_query_duration_seconds_bucket[5m])) * 1000
```

### Query Error Rate

```promql
rate(db_query_errors_total[5m]) / rate(db_query_count_total[5m])
```

### Slow Query Rate

```promql
rate(db_query_slow_total[5m])
```

### Connection Pool Utilization

```promql
(db_connection_pool_active / db_connection_pool_total) * 100
```

### Queries Per Second

```promql
sum(rate(db_query_count_total[5m]))
```

## Grafana Dashboard

A pre-built dashboard is available at `monitoring/grafana/dashboards/database-performance-dashboard.json`.

### Dashboard Panels

1. **Query Execution Time (Percentiles)** - p50, p95, p99 trends
2. **Query Success vs Failure Rate** - Pie chart of outcomes
3. **Connection Pool Utilization** - Active vs Idle connections
4. **Slow Queries by Service** - Service-based filtering
5. **Active Connections Gauge** - Real-time active connections
6. **Queries Per Second Gauge** - QPS metric
7. **Slow Queries/sec Gauge** - Slow query rate

### Accessing the Dashboard

1. Open Grafana at `http://localhost:3000`
2. Navigate to Dashboards > Database Performance
3. Use service filter to focus on specific services

## Alert Thresholds

### Critical Alerts

- **Slow Query Spike**: 10x above average
- **Query Error Rate**: >5%
- **Connection Pool Exhaustion**: >95% utilization
- **Database Unreachable**: Metrics unavailable for >1 minute

### Warning Alerts

- **High Slow Query Rate**: >10 per minute
- **Connection Pool High**: >80% utilization
- **Query Timeout Rate**: >2% error rate
- **High Query Latency**: p95 > 500ms
- **N+1 Query Pattern**: Detected

## Interpreting Metrics

### Healthy Database Performance

- p95 query latency: < 100ms
- p99 query latency: < 500ms
- Error rate: < 0.5%
- Connection pool utilization: 20-60%
- No N+1 query patterns

### Performance Degradation Signs

1. **Increasing query latency** → Check for missing indexes or slow queries
2. **Rising error rate** → Check database logs and connectivity
3. **Connection pool near exhaustion** → Increase pool size or investigate connection leaks
4. **Spike in N+1 queries** → Review recent code changes for inefficient data access

## Best Practices

### 1. Regular Monitoring

- Check dashboard daily for trends
- Set up alerts for critical thresholds
- Review slow query logs weekly

### 2. Query Optimization

- Use `@Query` annotations for complex queries
- Enable lazy loading cautiously (N+1 risk)
- Add indexes for frequently queried columns
- Use batch processing for bulk operations

### 3. Connection Pool Management

- Monitor idle connections (high idle = waste)
- Monitor active connections (approaching max = bottleneck)
- Adjust pool size based on peak usage
- Enable leak detection threshold

### 4. Development Practices

- Run with `spring.profiles.active=dev` to enable SQL logging
- Review slow query logs before committing
- Test with production-like data volumes
- Use load testing to identify bottlenecks

## Troubleshooting

### High Query Latency

1. Check Prometheus: `db.query.duration` percentiles
2. Review slow queries in application logs
3. Check database statistics with:
   ```sql
   SELECT query, mean_time, calls
   FROM pg_stat_statements
   ORDER BY mean_time DESC
   LIMIT 10;
   ```
4. Add indexes or optimize query

### Connection Pool Exhaustion

1. Check `db.connection.pool.active` gauge
2. Verify application isn't creating new connections per request
3. Check for connection leaks: `SELECT COUNT(*) FROM pg_stat_activity;`
4. Increase pool size in HikariCP config
5. Review slow queries (blocking connections)

### N+1 Query Issues

1. Enable SQL logging in development
2. Count queries logged for single operation
3. Use `@BatchSize` annotation on associations
4. Consider explicit JPA queries with fetch joins
5. Use `@EntityGraph` for query optimization

## Related Documentation

- [Structured Logging Guide](STRUCTURED_LOGGING_GUIDE.md)
- [Query Optimization Guide](QUERY_OPTIMIZATION_GUIDE.md)
- [Monitoring Guide](MONITORING_GUIDE.md)
