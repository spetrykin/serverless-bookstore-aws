package com.serhii.bookstore.recs.dto;

import com.serhii.bookstore.catalog.dto.BookResponse;
import java.util.List;

/**
 * {@code source} is {@code "personalized"} (engine ran, produced at least
 * one usable recommendation) or {@code "fallback"} (no order history yet,
 * or the engine failed — see {@code recs.service.RecommendationService} for why a fallback response is never silent in the
 * logs even though it's an ordinary response here).
 */
public record RecommendationResponse(List<BookResponse> books, String source) {

    public static final String SOURCE_PERSONALIZED = "personalized";
    public static final String SOURCE_FALLBACK = "fallback";
}
