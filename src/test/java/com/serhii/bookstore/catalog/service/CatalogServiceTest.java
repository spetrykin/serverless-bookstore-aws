package com.serhii.bookstore.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.repository.BookRepository;
import com.serhii.bookstore.common.web.Page;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private BookRepository bookRepository;

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(bookRepository);
    }

    @Test
    void listsVisibleBooksAndCarriesCursorThrough() {
        Book book = new Book("book-1", "Effective Java", 4500, 3, null, true, Instant.now());
        when(bookRepository.listVisible(null, 20)).thenReturn(new Page<>(List.of(book), "next-cursor"));

        Page<BookResponse> page = catalogService.listVisibleBooks(null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).bookId()).isEqualTo("book-1");
        assertThat(page.nextCursor()).isEqualTo("next-cursor");
    }

    @Test
    void rejectsInvalidCursor() {
        when(bookRepository.listVisible("garbage", 20)).thenThrow(new IllegalArgumentException("bad cursor"));

        assertThatThrownBy(() -> catalogService.listVisibleBooks("garbage"))
                .isInstanceOf(ValidationException.class);
    }
}