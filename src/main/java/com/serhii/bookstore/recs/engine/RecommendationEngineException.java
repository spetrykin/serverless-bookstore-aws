package com.serhii.bookstore.recs.engine;

/**
 * Thrown by {@link RecommendationEngine} implementations that can fail (in
 * practice: {@link BedrockRecommendationEngine}) so
 * {@code recs.service.RecommendationService} has one exception type to
 * catch regardless of the underlying cause (throttling, access denied, a
 * malformed/missing tool-use response, ...). The specific cause is always
 * logged at the throw site — this type carries it
 * as {@link #getCause()} for tests/diagnostics, not as the primary signal.
 */
public class RecommendationEngineException extends RuntimeException {

    public RecommendationEngineException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecommendationEngineException(String message) {
        super(message);
    }
}
