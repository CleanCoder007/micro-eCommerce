package com.ecommerce.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseHealthIndicator extends AbstractHealthIndicator {

    private final DataSource dataSource;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            long startTime = System.currentTimeMillis();

            try (Connection connection = dataSource.getConnection()) {
                long responseTime = System.currentTimeMillis() - startTime;

                builder.up()
                    .withDetail("database", "Connected")
                    .withDetail("responseTime", responseTime + "ms");

                if (responseTime > 1000) {
                    builder.status("DEGRADED")
                        .withDetail("warning", "Database response time is high");
                }
            }
        } catch (Exception ex) {
            log.error("Database health check failed", ex);
            builder.down()
                .withDetail("error", ex.getMessage());
        }
    }
}
