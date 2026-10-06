package com.serhii.bookstore.recs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.repository.BookRepository;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.domain.OrderLine;
import com.serhii.bookstore.order.repository.OrderRepository;
import com.serhii.bookstore.recs.dto.RecommendationResponse;
import com.serhii.bookstore.recs.engine.RecommendationEngine;
import com.serhii.bookstore.recs.engine.RecommendationEngineException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the two-tier strategy: cold-start
 * (no order history) and engine-failure both land on the same fallback
 * path, purchased-book names are structurally excluded from candidates
 * before the engine ever sees them, and an engine returning a bookId
 * outside the candidate list is dropped rather than surfaced.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private RecommendationEngine engine;

    private RecommendationService service;

    private static Book book(String id, String name) {
        return new Book(id, name, 1000, 5, "photo.jpg", true, Instant.now());
    }

    private static Order orderOf(String... bookNames) {
        List<OrderLine> lines = List.of(bookNames).stream()
                .map(name -> new OrderLine("book-" + name, name, 1000, 1))
                .toList();
        return new Order("order-1", "user-1", lines, 1000L * lines.size(), Instant.now());
    }

    @BeforeEach
    void setUp() {
        service = new RecommendationService(orderRepository, bookRepository, engine);
    }

    @Test
    void newUserWithNoOrderHistoryGetsFallbackWithoutCallingTheEngine() {
        when(orderRepository.findByUserId(eq("user-1"), eq(null), anyInt())).thenReturn(new Page<>(List.of(), null));
        when(bookRepository.listVisible(eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(book("1", "A"), book("2", "B")), null));

        RecommendationResponse response = service.recommend("user-1");

        assertThat(response.source()).isEqualTo(RecommendationResponse.SOURCE_FALLBACK);
        assertThat(response.books()).hasSize(2);
        verify(engine, never()).recommend(anyList(), anyList());
    }

    @Test
    void userWithHistoryGetsAPersonalizedResponseFromTheEngine() {
        when(orderRepository.findByUserId(eq("user-1"), eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(orderOf("Effective Java")), null));
        when(bookRepository.listVisible(eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(book("1", "Clean Code"), book("2", "Refactoring")), null));
        when(engine.recommend(anyList(), anyList())).thenReturn(List.of("1"));

        RecommendationResponse response = service.recommend("user-1");

        assertThat(response.source()).isEqualTo(RecommendationResponse.SOURCE_PERSONALIZED);
        assertThat(response.books()).extracting("bookId").containsExactly("1");
    }

    @Test
    void alreadyPurchasedBooksAreExcludedFromCandidatesBeforeTheEngineSeesThem() {
        when(orderRepository.findByUserId(eq("user-1"), eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(orderOf("Effective Java")), null));
        when(bookRepository.listVisible(eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(book("1", "Effective Java"), book("2", "Clean Code")), null));
        when(engine.recommend(anyList(), anyList())).thenReturn(List.of("2"));

        service.recommend("user-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Book>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(engine).recommend(anyList(), candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue()).extracting("bookId").containsExactly("2");
    }

    @Test
    void engineFailureFallsBackInsteadOfPropagating() {
        when(orderRepository.findByUserId(eq("user-1"), eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(orderOf("Effective Java")), null));
        when(bookRepository.listVisible(eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(book("1", "Clean Code")), null));
        when(engine.recommend(anyList(), anyList())).thenThrow(new RecommendationEngineException("boom"));

        RecommendationResponse response = service.recommend("user-1");

        assertThat(response.source()).isEqualTo(RecommendationResponse.SOURCE_FALLBACK);
        assertThat(response.books()).hasSize(1);
    }

    @Test
    void engineReturningAnUnknownBookIdIsDroppedNotSurfaced() {
        when(orderRepository.findByUserId(eq("user-1"), eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(orderOf("Effective Java")), null));
        when(bookRepository.listVisible(eq(null), anyInt()))
                .thenReturn(new Page<>(List.of(book("1", "Clean Code")), null));
        // "999" was never in the candidate list handed to the engine — simulates a hallucinated id.
        when(engine.recommend(anyList(), anyList())).thenReturn(List.of("999"));

        RecommendationResponse response = service.recommend("user-1");

        assertThat(response.source()).isEqualTo(RecommendationResponse.SOURCE_FALLBACK);
    }
}
