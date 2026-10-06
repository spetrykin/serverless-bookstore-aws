package com.serhii.bookstore.order.dto;

import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.domain.OrderLine;
import java.time.Instant;
import java.util.List;

public record OrderResponse(String orderId, List<OrderLineResponse> lines, long totalCents, Instant createdAt) {

    public record OrderLineResponse(String bookId, String name, long priceCents, int quantity) {

        static OrderLineResponse from(OrderLine line) {
            return new OrderLineResponse(line.bookId(), line.name(), line.priceCents(), line.quantity());
        }
    }

    public static OrderResponse from(Order order) {
        List<OrderLineResponse> lines = order.lines().stream().map(OrderLineResponse::from).toList();
        return new OrderResponse(order.orderId(), lines, order.totalCents(), order.createdAt());
    }
}