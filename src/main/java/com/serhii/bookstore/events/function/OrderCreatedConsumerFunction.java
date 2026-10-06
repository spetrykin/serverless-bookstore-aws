package com.serhii.bookstore.events.function;

import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.serhii.bookstore.events.service.OrderCreatedConsumerService;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * EventBridge target for the {@code OrderCreated} rule (see
 * {@code template.yaml}'s {@code OrderCreatedConsumerFunction.Events.OrderCreated},
 * an {@code EventBridgeRule} source). {@link ScheduledEvent} is
 * {@code aws-lambda-java-events}' generic EventBridge envelope shape
 * ({@code id}/{@code source}/{@code detail-type}/{@code detail}/...) — the
 * class name is a historical leftover from CloudWatch Events' scheduled-rule
 * origins, not specific to scheduled triggers; it's the standard type for
 * any EventBridge rule target, custom {@code PutEvents} traffic included.
 */
@Component("orderCreatedConsumer")
public class OrderCreatedConsumerFunction implements Function<ScheduledEvent, Void> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumerFunction.class);

    private final OrderCreatedConsumerService service;

    public OrderCreatedConsumerFunction(OrderCreatedConsumerService service) {
        this.service = service;
    }

    @Override
    public Void apply(ScheduledEvent event) {
        Map<String, Object> detail = event.getDetail();
        Object orderIdValue = detail == null ? null : detail.get("orderId");
        Object userIdValue = detail == null ? null : detail.get("userId");
        String orderId = orderIdValue == null ? null : orderIdValue.toString();
        String userId = userIdValue == null ? null : userIdValue.toString();
        try {
            service.handle(orderId, userId);
        } catch (RuntimeException e) {
            log.error("OrderCreated consumer failed, eventId={}, orderId={}", event.getId(), orderId, e);
            // Rethrown deliberately — this is a real processing failure (malformed detail, DynamoDB
            // error), distinct from the handled "already processed" duplicate-delivery path in
            // OrderCreatedConsumerService, which returns normally on purpose. Rethrowing lets
            // EventBridge/Lambda's own async-invoke retry policy apply instead of silently dropping
            // a genuine failure.
            throw e;
        }
        return null;
    }
}