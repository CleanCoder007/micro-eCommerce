package com.ecommerce.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            long startTime = System.currentTimeMillis();

            String testMessage = "health-check-" + UUID.randomUUID();
            var future = kafkaTemplate.send("health-check-topic", testMessage);

            future.get(5, TimeUnit.SECONDS);

            long responseTime = System.currentTimeMillis() - startTime;

            builder.up()
                .withDetail("kafka", "Connected")
                .withDetail("responseTime", responseTime + "ms");

            if (responseTime > 1000) {
                builder.status("DEGRADED")
                    .withDetail("warning", "Kafka response time is high");
            }
        } catch (Exception ex) {
            log.error("Kafka health check failed", ex);
            builder.down()
                .withDetail("error", ex.getMessage());
        }
    }
}
