package com.ecommerce.common.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.Health;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseHealthIndicatorTest {

    private DatabaseHealthIndicator indicator;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        indicator = new DatabaseHealthIndicator(dataSource);
    }

    @Test
    void testDatabaseHealthUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);

        Health.Builder builder = new Health.Builder();
        indicator.doHealthCheck(builder);
        Health health = builder.build();

        assertEquals("UP", health.getStatus().toString());
        assertTrue(health.getDetails().containsKey("database"));
    }

    @Test
    void testDatabaseHealthDown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection failed"));

        Health.Builder builder = new Health.Builder();
        indicator.doHealthCheck(builder);
        Health health = builder.build();

        assertEquals("DOWN", health.getStatus().toString());
    }

    @Test
    void testDatabaseResponseTimeIsIncluded() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);

        Health.Builder builder = new Health.Builder();
        indicator.doHealthCheck(builder);
        Health health = builder.build();

        assertTrue(health.getDetails().containsKey("responseTime"));
    }
}
