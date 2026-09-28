package com.ecommerce.common.metrics;

import com.ecommerce.common.logging.LogContextHolder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class QueryPerformanceLogger implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final MeterRegistry meterRegistry;
    private static final ThreadLocal<AtomicInteger> QUERY_COUNT = ThreadLocal.withInitial(AtomicInteger::new);
    private static final int N_PLUS_ONE_THRESHOLD = 5;

    public QueryPerformanceLogger(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        trackQueryOperation("INSERT", event.getEntity().getClass().getSimpleName());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        trackQueryOperation("UPDATE", event.getEntity().getClass().getSimpleName());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        trackQueryOperation("DELETE", event.getEntity().getClass().getSimpleName());
    }

    private void trackQueryOperation(String operation, String entityName) {
        AtomicInteger queryCount = QUERY_COUNT.get();
        queryCount.incrementAndGet();

        Counter.builder("db.query.operation")
            .description("Database query operations by type")
            .tag("operation", operation)
            .tag("entity", entityName)
            .register(meterRegistry)
            .increment();

        if (queryCount.get() > N_PLUS_ONE_THRESHOLD) {
            log.warn("Potential N+1 query problem detected - {} queries executed for entity: {}", queryCount.get(), entityName);
            Counter.builder("db.query.n_plus_one")
                .description("Potential N+1 query occurrences")
                .tag("entity", entityName)
                .register(meterRegistry)
                .increment();
        }
    }

    public static void resetQueryCount() {
        AtomicInteger count = QUERY_COUNT.get();
        if (count.get() > N_PLUS_ONE_THRESHOLD) {
            log.debug("Total queries in request: {}", count.get());
        }
        count.set(0);
    }

    public static void clearQueryCount() {
        QUERY_COUNT.remove();
    }

    @Override
    public boolean requiresPostCommitHanding(PostInsertEvent event) {
        return false;
    }

    @Override
    public boolean requiresPostCommitHanding(PostUpdateEvent event) {
        return false;
    }

    @Override
    public boolean requiresPostCommitHanding(PostDeleteEvent event) {
        return false;
    }
}
