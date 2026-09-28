package com.ecommerce.common.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ErrorNotificationPublisherTest {

    private ErrorNotificationPublisher publisher;

    @Mock
    private KafkaTemplate<String, ErrorNotificationEvent> kafkaTemplate;

    @Mock
    private ListenableFuture<SendResult<String, ErrorNotificationEvent>> sendResultFuture;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new ErrorNotificationPublisher(kafkaTemplate);
    }

    @Test
    void testPublishErrorEvent() {
        ErrorNotificationEvent event = ErrorNotificationEvent.builder()
            .orderId("ORDER-001")
            .serviceName("order-service")
            .errorType("PAYMENT_DECLINED")
            .errorMessage("Payment declined due to insufficient funds")
            .traceId(UUID.randomUUID().toString())
            .severity("CRITICAL")
            .timestamp(LocalDateTime.now())
            .build();

        when(kafkaTemplate.send(any())).thenReturn(sendResultFuture);

        publisher.publishError(event);

        verify(kafkaTemplate, times(1)).send(any());
        assertNotNull(event.getEventId());
    }

    @Test
    void testPublishErrorEventWithNullOrderId() {
        ErrorNotificationEvent event = ErrorNotificationEvent.builder()
            .serviceName("payment-service")
            .errorType("DATABASE_CONNECTION_ERROR")
            .errorMessage("Connection timeout")
            .traceId(UUID.randomUUID().toString())
            .severity("CRITICAL")
            .timestamp(LocalDateTime.now())
            .build();

        when(kafkaTemplate.send(any())).thenReturn(sendResultFuture);

        publisher.publishError(event);

        verify(kafkaTemplate, times(1)).send(any());
    }

    @Test
    void testPublishErrorEventAsync() throws Exception {
        ErrorNotificationEvent event = ErrorNotificationEvent.builder()
            .orderId("ORDER-002")
            .serviceName("inventory-service")
            .errorType("INSUFFICIENT_INVENTORY")
            .errorMessage("Not enough stock available")
            .traceId(UUID.randomUUID().toString())
            .severity("HIGH")
            .timestamp(LocalDateTime.now())
            .build();

        when(kafkaTemplate.send(any())).thenReturn(sendResultFuture);

        CompletableFuture<Void> result = publisher.publishErrorAsync(event);

        assertNotNull(result);
    }

    @Test
    void testEventIdIsGenerated() {
        ErrorNotificationEvent event = ErrorNotificationEvent.builder()
            .orderId("ORDER-003")
            .serviceName("customer-service")
            .errorType("CUSTOMER_NOT_FOUND")
            .errorMessage("Customer does not exist")
            .traceId(UUID.randomUUID().toString())
            .severity("HIGH")
            .timestamp(LocalDateTime.now())
            .build();

        when(kafkaTemplate.send(any())).thenReturn(sendResultFuture);

        assertNull(event.getEventId());
        publisher.publishError(event);
        assertNotNull(event.getEventId());
    }
}
