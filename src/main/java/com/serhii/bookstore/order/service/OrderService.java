package com.serhii.bookstore.order.service;

import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.exception.BookNotFoundException;
import com.serhii.bookstore.catalog.repository.BookRepository;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.domain.OrderLine;
import com.serhii.bookstore.order.dto.OrderResponse;
import com.serhii.bookstore.order.dto.PlaceOrderRequest;
import com.serhii.bookstore.order.repository.OrderRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderService {

    private static final int PAGE_SIZE = 20;

    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(BookRepository bookRepository, OrderRepository orderRepository, OrderEventPublisher orderEventPublisher) {
        this.bookRepository = bookRepository;
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    public OrderResponse placeOrder(String userId, PlaceOrderRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new ValidationException("Order must contain at least one line");
        }

        List<OrderLine> lines = new ArrayList<>();
        for (PlaceOrderRequest.PlaceOrderLine requestedLine : request.lines()) {
            if (requestedLine.bookId() == null || requestedLine.bookId().isBlank()) {
                throw new ValidationException("bookId is required for every order line");
            }
            if (requestedLine.quantity() == null || requestedLine.quantity() <= 0) {
                throw new ValidationException("quantity must be greater than 0 for every order line");
            }
            // Not-found and hidden are deliberately the same 404 here (no enumeration of hidden
            // bookIds via the order endpoint) — see catalog.exception.BookNotFoundException.
            Book book = bookRepository.findById(requestedLine.bookId())
                    .filter(Book::visible)
                    .orElseThrow(() -> new BookNotFoundException(requestedLine.bookId()));
            lines.add(new OrderLine(book.bookId(), book.name(), book.priceCents(), requestedLine.quantity()));
        }

        long totalCents = lines.stream().mapToLong(OrderLine::lineTotalCents).sum();
        Order order = new Order(UUID.randomUUID().toString(), userId, lines, totalCents, Instant.now());

        // Atomic conditional stock decrement + order write — see DynamoDbOrderRepository. Throws
        // InsufficientStockException (409) if any line's stock ran out between this snapshot and commit.
        orderRepository.placeOrder(order);
        // Published only after the write above commits — EventBridge is never the source of truth
        // for the order (architecture-plan.md §1.2/§2), and OrderEventPublisher itself never lets a
        // publish failure surface here (best-effort side channel).
        orderEventPublisher.publish(order);
        return OrderResponse.from(order);
    }

    public Page<OrderResponse> listOrders(String userId, String cursor) {
        Page<Order> page;
        try {
            page = orderRepository.findByUserId(userId, cursor, PAGE_SIZE);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid pagination cursor");
        }
        return new Page<>(page.items().stream().map(OrderResponse::from).toList(), page.nextCursor());
    }
}