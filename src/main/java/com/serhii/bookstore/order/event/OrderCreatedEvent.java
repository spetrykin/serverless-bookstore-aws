package com.serhii.bookstore.order.event;

import com.serhii.bookstore.order.domain.Order;
import java.time.Instant;

/**
 * Minimal payload published to EventBridge as the {@code OrderCreated}
 * event detail — deliberately not the full {@code OrderResponse} shape.
 * This is a best-effort side channel for analytics/notifications
 * (architecture-plan.md §1.2/§2/§3), never a source of truth for the order
 * itself, so it doesn't need per-line price/name snapshots — {@code
 * lineCount} is enough for a log/analytics consumer to know the order's
 * shape without duplicating the full line data across two places.
 */
public record OrderCreatedEvent(String orderId, String userId, long totalCents, int lineCount, Instant createdAt) {

    public static OrderCreatedEvent from(Order order) {
        return new OrderCreatedEvent(order.orderId(), order.userId(), order.totalCents(), order.lines().size(), order.createdAt());
    }
}