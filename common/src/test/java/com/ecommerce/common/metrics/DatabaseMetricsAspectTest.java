package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.repository.Repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseMetricsAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    private MeterRegistry meterRegistry;

    @InjectMocks
    private DatabaseMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aspect = new DatabaseMetricsAspect(meterRegistry);
    }

    @Test
    void testTrackRepositoryMetrics_Success() throws Throwable {
        when(joinPoint.getSignature().getName()).thenReturn("findById");
        when(joinPoint.getTarget().getClass().getSimpleName()).thenReturn("CustomerRepository");
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.trackRepositoryMetrics(joinPoint);

        assertEquals("result", result);
        assertEquals(1, meterRegistry.counter("db.query.count", "service", "CustomerRepository", "method", "findById", "outcome", "success").count());
    }

    @Test
    void testTrackRepositoryMetrics_Failure() throws Throwable {
        when(joinPoint.getSignature().getName()).thenReturn("save");
        when(joinPoint.getTarget().getClass().getSimpleName()).thenReturn("OrderRepository");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("DB Error"));

        assertThrows(RuntimeException.class, () -> aspect.trackRepositoryMetrics(joinPoint));

        assertEquals(1, meterRegistry.counter("db.query.errors", "service", "OrderRepository", "method", "save").count());
    }

    @Test
    void testSlowQueryDetection() throws Throwable {
        when(joinPoint.getSignature().getName()).thenReturn("complexQuery");
        when(joinPoint.getTarget().getClass().getSimpleName()).thenReturn("PaymentRepository");
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(1100);
            return "result";
        });

        aspect.trackRepositoryMetrics(joinPoint);

        assertEquals(1, meterRegistry.counter("db.query.slow", "service", "PaymentRepository", "method", "complexQuery", "outcome", "success").count());
    }

    @Test
    void testQueryExecutionTimeMetric() throws Throwable {
        when(joinPoint.getSignature().getName()).thenReturn("update");
        when(joinPoint.getTarget().getClass().getSimpleName()).thenReturn("InventoryRepository");
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(100);
            return "result";
        });

        aspect.trackRepositoryMetrics(joinPoint);

        var timer = meterRegistry.timer("db.query.duration", "service", "InventoryRepository", "method", "update", "outcome", "success");
        assertEquals(1, timer.count());
        assert(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 100);
    }
}
