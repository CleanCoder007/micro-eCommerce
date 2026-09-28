package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class DatabaseMetricsAspect {

    private final MeterRegistry meterRegistry;

    public DatabaseMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(* org.springframework.data.repository.Repository+.*(..))")
    public Object trackRepositoryMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        long startTime = System.currentTimeMillis();
        boolean isSuccess = false;

        try {
            Object result = joinPoint.proceed();
            isSuccess = true;
            return result;
        } catch (Throwable e) {
            recordFailedQuery(className, methodName);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            recordQueryMetrics(className, methodName, duration, isSuccess);
        }
    }

    private void recordQueryMetrics(String className, String methodName, long duration, boolean isSuccess) {
        String outcome = isSuccess ? "success" : "failure";
        Tags tags = Tags.of(
            Tag.of("service", className),
            Tag.of("method", methodName),
            Tag.of("outcome", outcome)
        );

        Timer.builder("db.query.duration")
            .description("Database query execution time")
            .tags(tags)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS);

        Counter.builder("db.query.count")
            .description("Total database queries")
            .tags(tags)
            .register(meterRegistry)
            .increment();

        if (duration > 1000) {
            Counter.builder("db.query.slow")
                .description("Number of slow queries (>1000ms)")
                .tags(tags)
                .register(meterRegistry)
                .increment();

            log.warn("Slow query detected - Service: {}, Method: {}, Duration: {}ms", className, methodName, duration);
        }
    }

    private void recordFailedQuery(String className, String methodName) {
        Tags tags = Tags.of(
            Tag.of("service", className),
            Tag.of("method", methodName)
        );

        Counter.builder("db.query.errors")
            .description("Database query errors")
            .tags(tags)
            .register(meterRegistry)
            .increment();

        log.error("Query error - Service: {}, Method: {}", className, methodName);
    }
}
