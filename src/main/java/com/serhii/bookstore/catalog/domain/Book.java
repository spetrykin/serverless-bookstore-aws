package com.serhii.bookstore.catalog.domain;

import java.time.Instant;

/** {@code priceCents}/{@code count} are the price snapshot source for Order — see order.service.OrderService. */
public record Book(
        String bookId,
        String name,
        long priceCents,
        int count,
        String photoUrl,
        boolean visible,
        Instant createdAt) {
}