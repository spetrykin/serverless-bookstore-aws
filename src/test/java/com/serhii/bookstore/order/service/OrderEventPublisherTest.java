package com.serhii.bookstore.order.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.domain.OrderLine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers §1's "publish failure must never fail POST /orders" requirement —
 * every path here (SDK exception, partial-batch failure) must swallow, not
 * rethrow, since {@code OrderService} calls this only after the order write
 * already committed.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private EventBridgeClient client;

    private OrderEventPublisher publisher;

    private static Order order() {
        return new Order("order-1", "user-1",
                List.of(new OrderLine("book-1", "Effective Java", 4500, 2)),
                9000, Instant.now());
    }

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(new ObjectMapper());
        publisher.setClientForTesting(client);
    }

    @Test
    void publishesOrderCreatedDetail() {
        when(client.putEvents(any(PutEventsRequest.class)))
                .thenReturn(PutEventsResponse.builder().failedEntryCount(0).build());

        publisher.publish(order());

        verify(client).putEvents(any(PutEventsRequest.class));
    }

    @Test
    void doesNotThrowWhenEventBridgeReportsAPartialBatchFailure() {
        when(client.putEvents(any(PutEventsRequest.class)))
                .thenReturn(PutEventsResponse.builder()
                        .failedEntryCount(1)
                        .entries(PutEventsResultEntry.builder()
                                .errorCode("InternalFailure")
                                .errorMessage("boom")
                                .build())
                        .build());

        assertThatCode(() -> publisher.publish(order())).doesNotThrowAnyException();
    }

    @Test
    void doesNotThrowWhenTheSdkCallItselfThrows() {
        when(client.putEvents(any(PutEventsRequest.class))).thenThrow(RuntimeException.class);

        assertThatCode(() -> publisher.publish(order())).doesNotThrowAnyException();
    }
}