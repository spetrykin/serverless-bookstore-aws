package com.serhii.bookstore.events.service;

import com.serhii.bookstore.events.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Analytics/log stub for {@code OrderCreated} — deliberately minimal scope
 * (architecture-plan.md §3: "analytics/log", not a real analytics
 * pipeline). The only functionally load-bearing part of this class is the
 * idempotency check; the "processing" itself is a structured log line.
 */
@Component
public class OrderCreatedConsumerService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumerService.class);
    private static final String DETAIL_TYPE = "OrderCreated";

    private final ProcessedEventRepository processedEventRepository;

    public OrderCreatedConsumerService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    public void handle(String orderId, String userId) {
        if (orderId == null || orderId.isBlank()) {
            // Malformed detail is a real bug (publisher or rule misconfiguration), not a
            // duplicate-delivery case — let the caller's retry policy apply by not swallowing this
            // the same way a legitimate duplicate is swallowed below.
            throw new IllegalArgumentException("OrderCreated event missing orderId in detail");
        }
        boolean firstDelivery = processedEventRepository.markProcessedIfFirstDelivery(orderId, DETAIL_TYPE);
        if (!firstDelivery) {
            log.info("OrderCreated duplicate delivery, orderId={}, skipping", orderId);
            return;
        }
        log.info("OrderCreated processed, orderId={}, userId={}", orderId, userId);
    }
}