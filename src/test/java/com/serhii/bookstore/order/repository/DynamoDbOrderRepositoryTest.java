package com.serhii.bookstore.order.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.common.dynamodb.DynamoDbClientHolder;
import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.domain.OrderLine;
import com.serhii.bookstore.order.exception.InsufficientStockException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * Covers the stock-race case from architecture-plan.md §6.4: a conditional
 * decrement failing inside {@code transactWriteItems} must translate to
 * {@link InsufficientStockException} (409), never leak the raw SDK
 * exception, and never be confused with a genuinely unrelated transaction
 * cancellation (e.g. throttling), which should still propagate.
 */
@ExtendWith(MockitoExtension.class)
class DynamoDbOrderRepositoryTest {

    @Mock
    private DynamoDbClientHolder clientHolder;
    @Mock
    private DynamoDbClient rawClient;

    private DynamoDbOrderRepository repository;

    @BeforeEach
    void setUp() {
        when(clientHolder.rawClient()).thenReturn(rawClient);
        repository = new DynamoDbOrderRepository(clientHolder);
    }

    private static Order order() {
        return new Order("order-1", "user-1",
                List.of(new OrderLine("book-1", "Effective Java", 4500, 2)),
                9000, Instant.now());
    }

    @Test
    void placesOrderSuccessfully() {
        when(rawClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        assertThatCode(() -> repository.placeOrder(order())).doesNotThrowAnyException();
        verify(rawClient).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    void translatesConditionalCheckFailedIntoInsufficientStock() {
        when(rawClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .cancellationReasons(CancellationReason.builder().code("ConditionalCheckFailed").build())
                        .build());

        assertThatThrownBy(() -> repository.placeOrder(order()))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void rethrowsTransactionCancellationUnrelatedToStock() {
        when(rawClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .cancellationReasons(CancellationReason.builder().code("ThrottlingError").build())
                        .build());

        assertThatThrownBy(() -> repository.placeOrder(order()))
                .isInstanceOf(TransactionCanceledException.class)
                .isNotInstanceOf(InsufficientStockException.class);
    }
}