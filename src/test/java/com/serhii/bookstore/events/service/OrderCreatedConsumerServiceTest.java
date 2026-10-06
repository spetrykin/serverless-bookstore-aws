package com.serhii.bookstore.events.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.events.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** EventBridge + Lambda is at-least-once delivery (architecture-plan.md §1.4) — this is what covers that a redelivered event doesn't get reprocessed. */
@ExtendWith(MockitoExtension.class)
class OrderCreatedConsumerServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private OrderCreatedConsumerService service;

    @Test
    void processesFirstDelivery() {
        service = new OrderCreatedConsumerService(processedEventRepository);
        when(processedEventRepository.markProcessedIfFirstDelivery("order-1", "OrderCreated")).thenReturn(true);

        assertThatCode(() -> service.handle("order-1", "user-1")).doesNotThrowAnyException();
    }

    @Test
    void skipsWithoutErrorOnDuplicateDelivery() {
        service = new OrderCreatedConsumerService(processedEventRepository);
        when(processedEventRepository.markProcessedIfFirstDelivery("order-1", "OrderCreated")).thenReturn(false);

        assertThatCode(() -> service.handle("order-1", "user-1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrderIdAsARealFailureNotADuplicate() {
        service = new OrderCreatedConsumerService(processedEventRepository);

        assertThatThrownBy(() -> service.handle(null, "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processedEventRepository);
    }

    @Test
    void rejectsBlankOrderId() {
        service = new OrderCreatedConsumerService(processedEventRepository);

        assertThatThrownBy(() -> service.handle("  ", "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processedEventRepository);
    }
}