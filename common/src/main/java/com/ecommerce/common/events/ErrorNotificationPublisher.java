package com.ecommerce.common.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class ErrorNotificationPublisher {

    private final KafkaTemplate<String, ErrorNotificationEvent> kafkaTemplate;

    @Value("${kafka.topics.error-events:error-events}")
    private String errorEventsTopic;

    public void publishError(ErrorNotificationEvent errorEvent) {
        try {
            errorEvent.setEventId(UUID.randomUUID().toString());

            Message<ErrorNotificationEvent> message = MessageBuilder
                .withPayload(errorEvent)
                .setHeader(KafkaHeaders.TOPIC, errorEventsTopic)
                .setHeader(KafkaHeaders.MESSAGE_KEY, errorEvent.getOrderId() != null ? errorEvent.getOrderId() : UUID.randomUUID().toString())
                .setHeader("X-Trace-ID", errorEvent.getTraceId())
                .setHeader("service-name", errorEvent.getServiceName())
                .build();

            kafkaTemplate.send(message).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish error event to Kafka - TraceID: {}", errorEvent.getTraceId(), ex);
                } else {
                    log.info("Error event published successfully - EventID: {}, TraceID: {}",
                        errorEvent.getEventId(), errorEvent.getTraceId());
                }
            });
        } catch (Exception ex) {
            log.error("Error publishing error notification event - TraceID: {}", errorEvent.getTraceId(), ex);
        }
    }

    public CompletableFuture<Void> publishErrorAsync(ErrorNotificationEvent errorEvent) {
        return CompletableFuture.runAsync(() -> publishError(errorEvent));
    }
}
