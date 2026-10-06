package com.serhii.bookstore.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.dto.CreateBookRequest;
import com.serhii.bookstore.catalog.dto.UpdateBookRequest;
import com.serhii.bookstore.catalog.exception.BookNotFoundException;
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
class AdminBookServiceTest {

    @Mock
    private BookRepository bookRepository;

    private AdminBookService adminBookService;

    @BeforeEach
    void setUp() {
        adminBookService = new AdminBookService(bookRepository);
    }

    @Test
    void createsBookAndDefaultsVisibleToTrueWhenOmitted() {
        BookResponse response = adminBookService.createBook(
                new CreateBookRequest("Effective Java", 4500L, 10, null, null));

        assertThat(response.visible()).isTrue();
        assertThat(response.priceCents()).isEqualTo(4500);
        assertThat(response.count()).isEqualTo(10);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> adminBookService.createBook(
                new CreateBookRequest(" ", 4500L, 10, null, null)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(bookRepository);
    }

    @Test
    void rejectsZeroPrice() {
        assertThatThrownBy(() -> adminBookService.createBook(
                new CreateBookRequest("Effective Java", 0L, 10, null, null)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(bookRepository);
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> adminBookService.createBook(
                new CreateBookRequest("Effective Java", -100L, 10, null, null)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(bookRepository);
    }

    @Test
    void rejectsNegativeCount() {
        assertThatThrownBy(() -> adminBookService.createBook(
                new CreateBookRequest("Effective Java", 4500L, -1, null, null)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(bookRepository);
    }

    @Test
    void allowsZeroCountOnCreate() {
        BookResponse response = adminBookService.createBook(
                new CreateBookRequest("Effective Java", 4500L, 0, null, true));

        assertThat(response.count()).isZero();
    }

    @Test
    void updateRequiresExplicitVisible() {
        assertThatThrownBy(() -> adminBookService.updateBook("book-1",
                new UpdateBookRequest("Effective Java", 4500L, 10, null, null)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(bookRepository);
    }

    @Test
    void updatesBook() {
        BookResponse response = adminBookService.updateBook("book-1",
                new UpdateBookRequest("Effective Java, 3rd Ed.", 5500L, 8, "https://example.com/p.jpg", false));

        assertThat(response.bookId()).isEqualTo("book-1");
        assertThat(response.visible()).isFalse();
        assertThat(response.priceCents()).isEqualTo(5500);
    }

    @Test
    void propagatesNotFoundFromRepositoryOnUpdate() {
        doThrow(new BookNotFoundException("book-1")).when(bookRepository).updateBook(any(Book.class));

        assertThatThrownBy(() -> adminBookService.updateBook("book-1",
                new UpdateBookRequest("Effective Java", 4500L, 10, null, true)))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void listsAllBooksIncludingHidden() {
        Book hidden = new Book("book-2", "Hidden Book", 100, 0, null, false, Instant.now());
        when(bookRepository.listAll(null, 20)).thenReturn(new Page<>(List.of(hidden), null));

        Page<BookResponse> page = adminBookService.listAllBooks(null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).visible()).isFalse();
    }
}