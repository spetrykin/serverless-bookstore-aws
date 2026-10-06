package com.serhii.bookstore.recs.engine;

import com.serhii.bookstore.catalog.domain.Book;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, no AWS call: the first few pre-filtered candidates, in
 * whatever order the caller passed them (that order
 * is GSI1 sort-key order, i.e. by {@code bookId}, not recency). {@code
 * purchasedBookNames} is unused here — the caller already excludes those
 * names from {@code candidateBooks} before either engine sees them — kept
 * as a parameter purely for interface parity with
 * {@link BedrockRecommendationEngine}, which does use it as prompt context.
 *
 * <p>Default engine ({@code bookstore.recs.mode} unset or {@code "mock"}).
 * Also the logic {@code recs.service.RecommendationService} falls back to
 * directly (not through this bean) when bedrock-mode fails.
 */
@Component
@ConditionalOnProperty(name = "bookstore.recs.mode", havingValue = "mock", matchIfMissing = true)
public class MockRecommendationEngine implements RecommendationEngine {

    public static final int MAX_RECOMMENDATIONS = 5;

    @Override
    public List<String> recommend(List<String> purchasedBookNames, List<Book> candidateBooks) {
        return candidateBooks.stream().map(Book::bookId).limit(MAX_RECOMMENDATIONS).toList();
    }
}
