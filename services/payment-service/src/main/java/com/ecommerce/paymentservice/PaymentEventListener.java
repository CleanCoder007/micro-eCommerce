package com.ecommerce.paymentservice;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.RefundInitiatedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
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

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Payment Event Listener - Handles Saga Pattern with Compensating Transactions
 *
 * Forward Transactions:
 * - handleInventoryReserved: Process payment when inventory is reserved
 *
 * Compensating Transactions:
 * - handleOrderCancelled: Refund payment when order is cancelled (Saga rollback)
 *
 * This implements choreography-based Saga pattern where payment is the critical
 * resource that must be refunded if subsequent steps fail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentRepository repository;
    private final EventPublisher eventPublisher;

    /**
     * Forward Transaction: Process payment when inventory is reserved.
     * Publishes PaymentProcessedEvent on success or PaymentFailedEvent on failure.
     */
    @KafkaListener(topics = "inventory-reserved", groupId = "payment-group")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackHandleInventoryReserved")
    @Retry(name = "paymentService")
    @TimeLimiter(name = "paymentService")
    public void handleInventoryReserved(@Payload InventoryReservedEvent event,
                                        Acknowledgment ack,
                                        @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {
        try {
            log.info("Handling inventory reserved event for order: {} - Processing payment", event.getOrderId());

            Payment payment = new Payment();
            payment.setOrderId(event.getOrderId());
            payment.setAmount(new BigDecimal("99.99"));
            payment.setStatus(PaymentStatus.PROCESSED);
            Payment savedPayment = repository.save(payment);

            PaymentProcessedEvent processedEvent = new PaymentProcessedEvent(
                savedPayment.getId(),
                event.getOrderId(),
                savedPayment.getAmount()
            );

            eventPublisher.publishEvent(processedEvent, "payment-processed",
                event.getEventId(), event.getEventId());

            log.info("✓ Payment processed successfully for order: {} (amount: {})",
                event.getOrderId(), savedPayment.getAmount());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling inventory reserved event for order: {} from topic: {}", event.getOrderId(), topic, e);

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                event.getOrderId(),
                event.getProductId(),
                event.getQuantity(),
                "Payment processing exception: " + e.getMessage()
            );
            eventPublisher.publishEvent(failedEvent, "payment-failed",
                event.getEventId(), event.getEventId());

            throw new DeadLetterException("Failed to process inventory reserved event: " + e.getMessage(),
                topic != null ? topic : "inventory-reserved", event.getOrderId().toString());
        }
    }

    public void fallbackHandleInventoryReserved(InventoryReservedEvent event,
                                               Acknowledgment ack,
                                               @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                               Exception ex) {
        log.error("Fallback: Circuit breaker open or max retries exceeded for inventory reserved event for order: {}",
            event.getOrderId(), ex);
    }

    /**
     * Compensating Transaction: Refund payment when order is cancelled.
     * This is a Saga rollback - reverse the payment charge.
     */
    @KafkaListener(topics = "order-cancelled", groupId = "payment-group")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackHandleOrderCancelled")
    @Retry(name = "paymentService")
    @TimeLimiter(name = "paymentService")
    public void handleOrderCancelled(@Payload OrderCancelledEvent event,
                                     Acknowledgment ack,
                                     @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {
        try {
            log.info("Handling order cancelled event for order: {} - Processing refund (Compensating Transaction)",
                event.getOrderId());

            Optional<Payment> paymentOpt = repository.findByOrderId(event.getOrderId());

            if (paymentOpt.isPresent()) {
                Payment payment = paymentOpt.get();

                if (payment.getStatus() == PaymentStatus.PROCESSED) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    repository.save(payment);

                    RefundCompletedEvent refundCompletedEvent = new RefundCompletedEvent(
                        event.getOrderId(),
                        payment.getId(),
                        payment.getAmount()
                    );
                    eventPublisher.publishEvent(refundCompletedEvent, "refund-completed",
                        event.getEventId(), event.getEventId());

                    log.info("✓ Payment refunded successfully for order: {} (amount: {}, reason: {})",
                        event.getOrderId(), payment.getAmount(), event.getReason());
                } else {
                    log.warn("⚠ Payment for order: {} is not in PROCESSED state, status: {}",
                        event.getOrderId(), payment.getStatus());
                }
            } else {
                log.warn("⚠ No payment found for order: {} - Cannot process refund", event.getOrderId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling order cancelled event for order: {} from topic: {}", event.getOrderId(), topic, e);
            throw new DeadLetterException("Failed to process order cancelled event: " + e.getMessage(),
                topic != null ? topic : "order-cancelled", event.getOrderId().toString());
        }
    }

    public void fallbackHandleOrderCancelled(OrderCancelledEvent event,
                                            Acknowledgment ack,
                                            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                            Exception ex) {
        log.error("Fallback: Circuit breaker open or max retries exceeded for order cancelled event for order: {}",
            event.getOrderId(), ex);
    }
}
