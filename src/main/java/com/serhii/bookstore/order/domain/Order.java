package com.serhii.bookstore.order.domain;

import java.time.Instant;
import java.util.List;

public record Order(String orderId, String userId, List<OrderLine> lines, long totalCents, Instant createdAt) {
}