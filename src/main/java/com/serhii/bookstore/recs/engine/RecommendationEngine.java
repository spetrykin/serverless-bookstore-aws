package com.serhii.bookstore.recs.engine;

import com.serhii.bookstore.catalog.domain.Book;
import java.util.List;

/**
 * Mock ↔ real Bedrock behind one interface —
 * {@code recs.service.RecommendationService} depends only on this, never on
 * which implementation is active. {@code candidateBooks} is already
 * pre-filtered by the caller to exclude anything matching
 * {@code purchasedBookNames} (structural guarantee, not a prompt
 * instruction the model could ignore) and
 * capped to a small page, so implementations don't need their own paging
 * or purchased-name filtering logic.
 */
public interface RecommendationEngine {

    /** Returns up to a handful of {@code bookId}s chosen from {@code candidateBooks}. */
    List<String> recommend(List<String> purchasedBookNames, List<Book> candidateBooks);
}
