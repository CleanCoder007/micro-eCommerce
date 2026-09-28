package com.ecommerce.common.config;

import com.ecommerce.common.metrics.ConnectionPoolMetricsCollector;
import com.ecommerce.common.metrics.DatabaseMetricsAspect;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.sql.DataSource;

@AutoConfiguration
@EnableAspectJAutoProxy
@Slf4j
@ConditionalOnProperty(name = "metrics.database.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseMetricsConfiguration {

    @Bean
    public DatabaseMetricsAspect databaseMetricsAspect(MeterRegistry meterRegistry) {
        log.info("Enabling database metrics collection via DatabaseMetricsAspect");
        return new DatabaseMetricsAspect(meterRegistry);
    }

    @Bean
    public ConnectionPoolMetricsCollector connectionPoolMetricsCollector(MeterRegistry meterRegistry, DataSource dataSource) {
        log.info("Enabling connection pool metrics collection");
        return new ConnectionPoolMetricsCollector(meterRegistry, dataSource);
    }
}
