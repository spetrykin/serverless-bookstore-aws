package com.serhii.bookstore.order.repository;

import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.order.domain.Order;

public interface OrderRepository {

    /**
     * Atomically decrements every line's Book stock and writes the Order in
     * one transaction — all lines succeed or none do. Throws
     * {@link com.serhii.bookstore.order.exception.InsufficientStockException}
     * if any line's stock is insufficient at commit time.
     */
    void placeOrder(Order order);

    Page<Order> findByUserId(String userId, String cursor, int limit);
}