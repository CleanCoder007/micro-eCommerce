package com.ecommerce.inventoryservice;

import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.InventoryReleasedEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.exception.DeadLetterException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Inventory Event Listener - Handles Saga Pattern with Compensating Transactions
 *
 * Forward Transactions:
 * - handleOrderCreated: Reserve inventory when order is created
 *
 * Compensating Transactions:
 * - handlePaymentFailed: Release reserved inventory when payment fails (Saga rollback)
 *
 * This implements choreography-based Saga pattern where services emit events
 * and other services listen and react, including compensating actions on failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final InventoryRepository repository;
    private final EventPublisher eventPublisher;

    /**
     * Forward Transaction: Reserve inventory when order is created.
     * Publishes InventoryReservedEvent on success or InventoryFailedEvent on failure.
     */
    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackHandleOrderCreated")
    @Retry(name = "inventoryService")
    @TimeLimiter(name = "inventoryService")
    public void handleOrderCreated(@Payload OrderCreatedEvent event,
                                   Acknowledgment ack,
                                   @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {
        try {
            log.info("Handling order created event for order: {} - Reserving inventory for product: {}, quantity: {}",
                event.getOrderId(), event.getProductId(), event.getQuantity());

            Optional<Inventory> inventoryOpt = repository.findByProductId(event.getProductId());

            if (inventoryOpt.isPresent() && inventoryOpt.get().getQuantity() >= event.getQuantity()) {
                Inventory inventory = inventoryOpt.get();
                inventory.setQuantity(inventory.getQuantity() - event.getQuantity());
                repository.save(inventory);

                InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                    event.getOrderId(),
                    event.getProductId(),
                    event.getQuantity()
                );
                eventPublisher.publishEvent(reservedEvent, "inventory-reserved",
                    event.getEventId(), event.getEventId());

                log.info("✓ Inventory reserved successfully for order: {} (product: {}, qty: {})",
                    event.getOrderId(), event.getProductId(), event.getQuantity());
            } else {
                InventoryFailedEvent failedEvent = new InventoryFailedEvent(event.getOrderId());
                eventPublisher.publishEvent(failedEvent, "inventory-failed",
                    event.getEventId(), event.getEventId());

                log.warn("✗ Inventory reservation failed for order: {} - insufficient stock for product: {}",
                    event.getOrderId(), event.getProductId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling order created event for order: {} from topic: {}", event.getOrderId(), topic, e);
            throw new DeadLetterException("Failed to process order created event: " + e.getMessage(),
                topic != null ? topic : "order-created", event.getOrderId().toString());
        }
    }

    public void fallbackHandleOrderCreated(OrderCreatedEvent event,
                                          Acknowledgment ack,
                                          @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                          Exception ex) {
        log.error("Fallback: Circuit breaker open or max retries exceeded for order created event for order: {}",
            event.getOrderId(), ex);
    }

    /**
     * Compensating Transaction: Release reserved inventory when payment fails.
     * This is a Saga rollback - reverse the inventory reservation.
     */
    @KafkaListener(topics = "payment-failed", groupId = "inventory-group")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackHandlePaymentFailed")
    @Retry(name = "inventoryService")
    @TimeLimiter(name = "inventoryService")
    public void handlePaymentFailed(@Payload PaymentFailedEvent event,
                                    Acknowledgment ack,
                                    @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {
        try {
            log.info("Handling payment failed event for order: {} - Releasing inventory (Compensating Transaction)",
                event.getOrderId());

            if (event.getProductId() != null && event.getQuantity() != null) {
                Optional<Inventory> inventoryOpt = repository.findByProductId(event.getProductId());

                if (inventoryOpt.isPresent()) {
                    Inventory inventory = inventoryOpt.get();
                    inventory.setQuantity(inventory.getQuantity() + event.getQuantity());
                    repository.save(inventory);

                    InventoryReleasedEvent releasedEvent = new InventoryReleasedEvent(
                        event.getOrderId(),
                        event.getProductId(),
                        event.getQuantity()
                    );
                    eventPublisher.publishEvent(releasedEvent, "inventory-released",
                        event.getEventId(), event.getEventId());

                    log.info("✓ Inventory released successfully for order: {} (Saga compensation - product: {}, qty: {})",
                        event.getOrderId(), event.getProductId(), event.getQuantity());
                } else {
                    log.warn("⚠ Could not find inventory to release for product: {}", event.getProductId());
                }
            } else {
                log.warn("⚠ Payment failed event missing product/quantity info for order: {}", event.getOrderId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling payment failed event for order: {} from topic: {}", event.getOrderId(), topic, e);
            throw new DeadLetterException("Failed to process payment failed event: " + e.getMessage(),
                topic != null ? topic : "payment-failed", event.getOrderId().toString());
        }
    }

    public void fallbackHandlePaymentFailed(PaymentFailedEvent event,
                                           Acknowledgment ack,
                                           @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                           Exception ex) {
        log.error("Fallback: Circuit breaker open or max retries exceeded for payment failed event for order: {}",
            event.getOrderId(), ex);
    }
}
