package com.serhii.bookstore.recs.service;

import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.repository.BookRepository;
import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.repository.OrderRepository;
import com.serhii.bookstore.recs.dto.RecommendationResponse;
import com.serhii.bookstore.recs.engine.RecommendationEngine;
import com.serhii.bookstore.recs.engine.RecommendationEngineException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Two-tier strategy: personalized when the user has
 * order history and the engine succeeds, deterministic fallback otherwise
 * — cold-start (no history) and engine-failure both land here, on purpose,
 * not two separate code paths.
 */
@Component
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    /** Most-recent-first (DynamoDbOrderRepository sorts descending). */
    private static final int ORDER_HISTORY_LIMIT = 20;
    private static final int CANDIDATE_LIMIT = 30;
    private static final int FALLBACK_LIMIT = 5;

    private final OrderRepository orderRepository;
    private final BookRepository bookRepository;
    private final RecommendationEngine engine;

    public RecommendationService(OrderRepository orderRepository, BookRepository bookRepository, RecommendationEngine engine) {
        this.orderRepository = orderRepository;
        this.bookRepository = bookRepository;
        this.engine = engine;
    }

    public RecommendationResponse recommend(String userId) {
        List<String> purchasedBookNames = purchasedBookNames(userId);
        List<Book> candidates = candidateBooks(purchasedBookNames);

        if (purchasedBookNames.isEmpty()) {
            // Cold start — no order history to personalize from at all. Skip the engine call
            // entirely rather than ask it to "personalize" from nothing.
            log.info("recommendations: no order history, userId={}, using fallback", userId);
            return fallback(candidates);
        }

        try {
            List<String> recommendedIds = engine.recommend(purchasedBookNames, candidates);
            List<BookResponse> books = resolve(recommendedIds, candidates);
            if (books.isEmpty()) {
                log.warn("recommendations: engine returned no usable bookIds, userId={}, using fallback", userId);
                return fallback(candidates);
            }
            log.info("recommendations: personalized succeeded, userId={}, count={}", userId, books.size());
            return new RecommendationResponse(books, RecommendationResponse.SOURCE_PERSONALIZED);
        } catch (RecommendationEngineException e) {
            // The specific AWS-level cause was already logged at WARN inside the engine;
            // this line is the service-level consequence, not a
            // duplicate of that detail.
            log.warn("recommendations: engine failed, userId={}, falling back to deterministic recommendations, cause={}",
                    userId, e.getMessage());
            return fallback(candidates);
        }
    }

    private List<String> purchasedBookNames(String userId) {
        List<Order> recentOrders = orderRepository.findByUserId(userId, null, ORDER_HISTORY_LIMIT).items();
        // LinkedHashSet: de-dupe while keeping first-seen (most-recent-order) ordering — not that
        // order matters for the prompt today, but no reason to discard it for free.
        Set<String> names = new LinkedHashSet<>();
        for (Order order : recentOrders) {
            order.lines().forEach(line -> names.add(line.name()));
        }
        return List.copyOf(names);
    }

    private List<Book> candidateBooks(List<String> purchasedBookNames) {
        Set<String> purchased = Set.copyOf(purchasedBookNames);
        return bookRepository.listVisible(null, CANDIDATE_LIMIT).items().stream()
                .filter(book -> book.count() > 0)
                // Structural exclusion, not a prompt instruction the model could ignore:
                // an already-purchased book never reaches either engine.
                .filter(book -> !purchased.contains(book.name()))
                .toList();
    }

    private static List<BookResponse> resolve(List<String> recommendedIds, List<Book> candidates) {
        Map<String, Book> byId = candidates.stream().collect(Collectors.toMap(Book::bookId, Function.identity()));
        return recommendedIds.stream()
                .map(byId::get)
                // Defensive: an engine could return a bookId outside the candidate list it was
                // given (a real LLM can hallucinate an id) — silently drop rather than surface a
                // book that was never actually offered as a candidate.
                .filter(Objects::nonNull)
                .map(BookResponse::from)
                .toList();
    }

    private static RecommendationResponse fallback(List<Book> candidates) {
        List<BookResponse> books = candidates.stream().limit(FALLBACK_LIMIT).map(BookResponse::from).toList();
        return new RecommendationResponse(books, RecommendationResponse.SOURCE_FALLBACK);
    }
}
