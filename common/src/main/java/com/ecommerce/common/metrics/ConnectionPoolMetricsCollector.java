package com.ecommerce.common.metrics;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Slf4j
@ConditionalOnClass(HikariDataSource.class)
public class ConnectionPoolMetricsCollector {

    private final MeterRegistry meterRegistry;
    private HikariDataSource hikariDataSource;

    public ConnectionPoolMetricsCollector(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        if (dataSource instanceof HikariDataSource) {
            this.hikariDataSource = (HikariDataSource) dataSource;
            initializeConnectionPoolMetrics();
        }
    }

    private void initializeConnectionPoolMetrics() {
        if (hikariDataSource == null) {
            log.warn("HikariDataSource not available for metrics");
            return;
        }

        try {
            Gauge.builder("db.connection.pool.active", () -> hikariDataSource.getHikariPoolMXBean().getActiveConnections())
                .description("Number of active database connections")
                .register(meterRegistry);

            Gauge.builder("db.connection.pool.idle", () -> hikariDataSource.getHikariPoolMXBean().getIdleConnections())
                .description("Number of idle database connections")
                .register(meterRegistry);

            Gauge.builder("db.connection.pool.total", () -> hikariDataSource.getHikariPoolMXBean().getTotalConnections())
                .description("Total number of database connections")
                .register(meterRegistry);

            Gauge.builder("db.connection.pool.pending", () -> hikariDataSource.getHikariPoolMXBean().getPendingThreads())
                .description("Number of threads waiting for a connection")
                .register(meterRegistry);

            log.info("Connection pool metrics initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize connection pool metrics: {}", e.getMessage());
        }
    }
}
