package com.serhii.bookstore.order.dto;

import java.util.List;

public record PlaceOrderRequest(List<PlaceOrderLine> lines) {

    public record PlaceOrderLine(String bookId, Integer quantity) {
    }
}