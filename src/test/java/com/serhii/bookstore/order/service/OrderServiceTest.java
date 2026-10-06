package com.serhii.bookstore.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.exception.BookNotFoundException;
import com.serhii.bookstore.catalog.repository.BookRepository;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.dto.OrderResponse;
import com.serhii.bookstore.order.dto.PlaceOrderRequest;
import com.serhii.bookstore.order.dto.PlaceOrderRequest.PlaceOrderLine;
import com.serhii.bookstore.order.exception.InsufficientStockException;
import com.serhii.bookstore.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventPublisher orderEventPublisher;

    private OrderService orderService;

    private static Book visibleBook() {
        return new Book("book-1", "Effective Java", 4500, 10, "https://example.com/photo.jpg", true, Instant.now());
    }

    @Test
    void placesOrderAndSnapshotsPriceAndName() {
        orderService = new OrderService(bookRepository, orderRepository, orderEventPublisher);
        when(bookRepository.findById("book-1")).thenReturn(Optional.of(visibleBook()));

        OrderResponse response = orderService.placeOrder("user-1",
                new PlaceOrderRequest(List.of(new PlaceOrderLine("book-1", 2))));

        assertThat(response.totalCents()).isEqualTo(9000);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).priceCents()).isEqualTo(4500);
        assertThat(response.lines().get(0).name()).isEqualTo("Effective Java");
        // Published only after the repository write commits (architecture-plan.md §1.2).
        verify(orderEventPublisher).publish(any(Order.class));
    }

    @Test
    void rejectsEmptyOrder() {
        orderService = new OrderService(bookRepository, orderRepository, orderEventPublisher);

        assertThatThrownBy(() -> orderService.placeOrder("user-1", new PlaceOrderRequest(List.of())))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(bookRepository, orderRepository, orderEventPublisher);
    }

    @Test
    void rejectsNonPositiveQuantity() {
        orderService = new OrderService(bookRepository, orderRepository, orderEventPublisher);

        assertThatThrownBy(() -> orderService.placeOrder("user-1",
                new PlaceOrderRequest(List.of(new PlaceOrderLine("book-1", 0)))))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(bookRepository, orderRepository, orderEventPublisher);
    }

    @Test
    void rejectsUnknownBookAsNotFound() {
        orderService = new OrderService(bookRepository, orderRepository, orderEventPublisher);
        when(bookRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder("user-1",
                new PlaceOrderRequest(List.of(new PlaceOrderLine("missing", 1)))))
                .isInstanceOf(BookNotFoundException.class);
    }

    /** Same 404 as a genuinely nonexistent book — see catalog.exception.BookNotFoundException's no-enumeration reasoning. */
    @Test
    void rejectsHiddenBookAsNotFoundNotAsSomeOtherError() {
        orderService = new OrderService(bookRepository, orderRepository, orderEventPublisher);
        Book hidden = new Book("book-1", "Effective Java", 4500, 10, null, false, Instant.now());
        when(bookRepository.findById("book-1")).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> orderService.placeOrder("user-1",
                new PlaceOrderRequest(List.of(new PlaceOrderLine("book-1", 1)))))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void propagatesInsufficientStockFromRepository() {
        orderService = new OrderService(bookRepository, orderRepository, orderEventPublisher);
        when(bookRepository.findById("book-1")).thenReturn(Optional.of(visibleBook()));
        doThrow(new InsufficientStockException()).when(orderRepository).placeOrder(any(Order.class));

        assertThatThrownBy(() -> orderService.placeOrder("user-1",
                new PlaceOrderRequest(List.of(new PlaceOrderLine("book-1", 1)))))
                .isInstanceOf(InsufficientStockException.class);
        // The repository write never committed, so publish must never fire (architecture-plan.md §1.2).
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void listsOrdersForUser() {
        orderService = new OrderService(bookRepository, orderRepository, orderEventPublisher);
        Order order = new Order("order-1", "user-1",
                List.of(new com.serhii.bookstore.order.domain.OrderLine("book-1", "Effective Java", 4500, 1)),
                4500, Instant.now());
        when(orderRepository.findByUserId("user-1", null, 20)).thenReturn(new Page<>(List.of(order), null));

        Page<OrderResponse> page = orderService.listOrders("user-1", null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).orderId()).isEqualTo("order-1");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void rejectsInvalidCursor() {
        orderService = new OrderService(bookRepository, orderRepository, orderEventPublisher);
        when(orderRepository.findByUserId("user-1", "garbage", 20)).thenThrow(new IllegalArgumentException("bad cursor"));

        assertThatThrownBy(() -> orderService.listOrders("user-1", "garbage"))
                .isInstanceOf(ValidationException.class);
    }
}