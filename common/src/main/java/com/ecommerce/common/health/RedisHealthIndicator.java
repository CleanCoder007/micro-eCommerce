package com.ecommerce.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisHealthIndicator extends AbstractHealthIndicator {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            long startTime = System.currentTimeMillis();

            String testKey = "health-check-" + System.nanoTime();
            redisTemplate.opsForValue().set(testKey, "up");
            String value = redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);

            long responseTime = System.currentTimeMillis() - startTime;

            if ("up".equals(value)) {
                builder.up()
                    .withDetail("redis", "Connected")
                    .withDetail("responseTime", responseTime + "ms");

                if (responseTime > 500) {
                    builder.status("DEGRADED")
                        .withDetail("warning", "Redis response time is high");
                }
            } else {
                builder.down()
                    .withDetail("error", "Redis test failed");
            }
        } catch (Exception ex) {
            log.error("Redis health check failed", ex);
            builder.down()
                .withDetail("error", ex.getMessage());
        }
    }
}
