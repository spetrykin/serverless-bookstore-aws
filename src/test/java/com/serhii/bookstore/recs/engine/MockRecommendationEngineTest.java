package com.serhii.bookstore.recs.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.serhii.bookstore.catalog.domain.Book;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockRecommendationEngineTest {

    private final MockRecommendationEngine engine = new MockRecommendationEngine();

    private static Book book(String id) {
        return new Book(id, "Book " + id, 1000, 5, "photo.jpg", true, Instant.now());
    }

    @Test
    void returnsFirstCandidatesUpToTheLimit() {
        List<Book> candidates = List.of(book("1"), book("2"), book("3"), book("4"), book("5"), book("6"));

        List<String> result = engine.recommend(List.of("Some Other Book"), candidates);

        assertThat(result).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void returnsAllCandidatesWhenFewerThanTheLimit() {
        List<Book> candidates = List.of(book("1"), book("2"));

        List<String> result = engine.recommend(List.of(), candidates);

        assertThat(result).containsExactly("1", "2");
    }

    @Test
    void returnsEmptyWhenNoCandidates() {
        assertThat(engine.recommend(List.of(), List.of())).isEmpty();
    }
}
