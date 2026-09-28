# Query Optimization Guide

## Overview

This guide provides strategies for identifying and optimizing slow database queries in the micro-ecommerce platform using the metrics and monitoring infrastructure.

## Identifying Slow Queries

### 1. Via Grafana Dashboard

1. Open Database Performance Dashboard in Grafana
2. Look at "Query Execution Time (Percentiles)" panel
3. If p95 > 100ms or p99 > 500ms, investigation needed
4. Filter by service to identify problem areas

### 2. Via Application Logs

With development profile enabled:

```bash
java -jar app.jar --spring.profiles.active=dev
```

Logs will show all executed SQL:

```
DEBUG org.hibernate.SQL - select customer0_.id as id1_0_0_, customer0_.name as name2_0_0_, customer0_.email as email3_0_0_ from customer customer0_ where customer0_.id=?
```

### 3. Via Database Query Log

PostgreSQL:

```sql
SELECT query, mean_time, calls
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;
```

## Common Performance Issues

### 1. N+1 Query Problem

**Symptom:** Many similar queries in logs for single operation

```
SELECT * FROM customers WHERE id = 1;  -- 1 query
SELECT * FROM orders WHERE customer_id = 1;  -- 1 query  
SELECT * FROM order_items WHERE order_id = ?;  -- N queries (one per order)
```

**Metric Alert:** `db.query.n_plus_one` counter incremented

**Solutions:**

#### a. Use Fetch Join in JPQL

```java
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.customer.id = :customerId")
List<Order> findOrdersWithItems(@Param("customerId") Long customerId);
```

#### b. Use @EntityGraph

```java
@EntityGraph(attributePaths = {"items"})
@Query("SELECT o FROM Order o WHERE o.customer.id = :customerId")
List<Order> findOrdersWithItems(@Param("customerId") Long customerId);
```

#### c. Configure Batch Size

```java
@Entity
@Table(name = "orders")
@BatchSize(size = 20)
public class Order {
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;
}
```

### 2. Missing Indexes

**Symptom:** Query execution time increases with data volume

**Example:**

```java
// Slow without index
List<Order> orders = orderRepository.findByCustomerId(123);
```

**Solution:** Add index to frequently queried columns

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_status_created", columnList = "status, created_at")
})
public class Order {
    @Column(name = "customer_id")
    private Long customerId;
    
    @Column(name = "status")
    private OrderStatus status;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

### 3. Missing Database Statistics

**Symptom:** Query plan is inefficient despite indexes

**Solution:** Update database statistics

PostgreSQL:
```sql
ANALYZE customer;
ANALYZE orders;
ANALYZE order_items;
```

### 4. Inefficient JOIN Conditions

**Bad Example:**

```java
@Query("SELECT o FROM Order o WHERE YEAR(o.createdAt) = :year")
List<Order> findOrdersByYear(@Param("year") int year);
```

**Good Example:**

```java
@Query("SELECT o FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt < :endDate")
List<Order> findOrdersByDateRange(@Param("startDate") LocalDateTime start, 
                                   @Param("endDate") LocalDateTime end);
```

### 5. Large Result Sets

**Problem:** Loading too much data into memory

**Solution:** Use pagination

```java
@Query("SELECT o FROM Order o WHERE o.customerId = :customerId")
Page<Order> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

// Usage
Page<Order> page = orderRepository.findByCustomerId(123, PageRequest.of(0, 20));
```

### 6. SELECT * Anti-Pattern

**Problem:** Fetching unnecessary columns

**Bad:**

```java
@Query("SELECT o FROM Order o")  // Fetches all columns
List<Order> findAll();
```

**Good:**

```java
@Query("SELECT new com.ecommerce.dto.OrderSummary(o.id, o.totalAmount, o.status) FROM Order o")
List<OrderSummary> findAllSummaries();
```

## Optimization Techniques

### 1. Query Optimization

```java
// BEFORE: N+1 queries
List<Order> orders = orderRepository.findAll();
orders.forEach(o -> System.out.println(o.getCustomer().getName())); // Triggers N queries

// AFTER: Fetch join in single query
@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.customer")
List<Order> findAllWithCustomers();
```

### 2. Batch Processing

```java
// Process large datasets efficiently
List<Order> orders = orderRepository.findOrdersToProcess();

List<List<Order>> batches = Lists.partition(orders, 100);
for (List<Order> batch : batches) {
    processAndSave(batch);
    entityManager.flush();
    entityManager.clear(); // Prevent memory buildup
}
```

### 3. Caching

```java
@Service
@Cacheable(cacheNames = "categories")
public List<Category> getAllCategories() {
    return categoryRepository.findAll();
}

@CachePut(cacheNames = "categories", key = "#result.id")
public Category updateCategory(Category category) {
    return categoryRepository.save(category);
}
```

### 4. Read-Only Queries

```java
@Query("SELECT o FROM Order o WHERE o.id = :id")
@Transactional(readOnly = true)
Order findOrderById(@Param("id") Long id);
```

### 5. Projections

```java
public interface OrderProjection {
    Long getId();
    BigDecimal getTotalAmount();
    String getStatus();
}

@Query("SELECT o FROM Order o WHERE o.id = :id")
OrderProjection findOrderProjection(@Param("id") Long id);
```

## Testing Optimization

### 1. Measure Before/After

```java
@Test
public void testCustomerFetchOptimization() {
    long start = System.currentTimeMillis();
    
    List<Order> orders = orderRepository.findOrdersWithItems(123);
    orders.forEach(o -> System.out.println(o.getItems().size()));
    
    long duration = System.currentTimeMillis() - start;
    System.out.println("Duration: " + duration + "ms");
    
    assert(duration < 100); // Should complete in <100ms
}
```

### 2. Load Testing

Use Apache JMeter or similar tool to test with realistic data volumes:

1. Create 100K+ customers and orders
2. Run queries and measure p95/p99
3. Compare before/after optimization

### 3. Verify Query Plan

PostgreSQL EXPLAIN:

```sql
EXPLAIN ANALYZE
SELECT o.* FROM orders o
WHERE o.customer_id = 123
ORDER BY o.created_at DESC
LIMIT 20;
```

Look for:
- Sequential scans (should use index for large tables)
- N-nested loop joins (use hash join if possible)
- Missing index warnings

## Database Connection Pool

### Configuration for Optimization

```yaml
spring:
  datasource:
    hikari:
      # Pool size = (core_count * 2) + effective_spindle_count
      # For 4 cores: 8-12 connections
      maximum-pool-size: 10
      minimum-idle: 2
      
      # Performance tuning
      connection-timeout: 20000  # 20 seconds
      idle-timeout: 600000       # 10 minutes
      max-lifetime: 1800000      # 30 minutes
      
      # Detect leaks
      leak-detection-threshold: 60000  # 60 seconds
      
      # Query performance
      auto-commit: true
      initialization-fail-timeout: 1
```

## SLO Targets

### Database Response Time SLOs

- p50 query latency: < 50ms
- p95 query latency: < 100ms  ✓ Alert if exceeded
- p99 query latency: < 500ms  ✓ Alert if exceeded

### Availability SLOs

- Query success rate: > 99.5%
- Connection pool availability: > 99.9%
- Database uptime: > 99.95%

## Monitoring and Alerting

### Key Metrics to Monitor

1. **db.query.duration** - Query response times
2. **db.query.slow** - Count of slow queries
3. **db.query.n_plus_one** - N+1 query detection
4. **db.connection.pool.active** - Connection usage
5. **db.query.errors** - Query failures

### Alert Rules

```yaml
- alert: SlowQuerySpike
  expr: rate(db_query_slow_total[5m]) > 10
  for: 5m
  annotations:
    summary: "{{ $labels.service }} experiencing slow queries"
```

## Checklists

### Before Deployment

- [ ] Run slow query logs on development
- [ ] Check p95/p99 latency in metrics
- [ ] Verify no new N+1 patterns
- [ ] Test with production-like data volume
- [ ] Review database query plan (EXPLAIN)
- [ ] Confirm indexes are in place

### Operational Monitoring

- [ ] Check Grafana dashboard daily
- [ ] Review slow query log weekly
- [ ] Analyze error rate trends
- [ ] Monitor connection pool usage
- [ ] Validate SLO compliance

## Related Documentation

- [Database Metrics Guide](DATABASE_METRICS_GUIDE.md)
- [Structured Logging Guide](STRUCTURED_LOGGING_GUIDE.md)
- [Monitoring Guide](MONITORING_GUIDE.md)
