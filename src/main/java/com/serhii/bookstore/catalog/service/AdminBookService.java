package com.serhii.bookstore.catalog.service;

import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.dto.CreateBookRequest;
import com.serhii.bookstore.catalog.dto.UpdateBookRequest;
import com.serhii.bookstore.catalog.repository.BookRepository;
import com.serhii.bookstore.common.web.Page;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Admin catalog CRUD — full book list (visible + hidden), create, update (incl. hide/show, price, restock). */
@Component
public class AdminBookService {

    private static final int PAGE_SIZE = 20;

    private final BookRepository bookRepository;

    public AdminBookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Page<BookResponse> listAllBooks(String cursor) {
        Page<Book> page;
        try {
            page = bookRepository.listAll(cursor, PAGE_SIZE);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid pagination cursor");
        }
        return new Page<>(page.items().stream().map(BookResponse::from).toList(), page.nextCursor());
    }

    public BookResponse createBook(CreateBookRequest request) {
        validate(request.name(), request.priceCents(), request.count());
        boolean visible = request.visible() == null || request.visible();
        Book book = new Book(
                UUID.randomUUID().toString(),
                request.name(),
                request.priceCents(),
                request.count(),
                request.photoUrl(),
                visible,
                Instant.now());
        bookRepository.createBook(book);
        return BookResponse.from(book);
    }

    public BookResponse updateBook(String bookId, UpdateBookRequest request) {
        validate(request.name(), request.priceCents(), request.count());
        if (request.visible() == null) {
            throw new ValidationException("Visible is required");
        }
        // createdAt is a placeholder here — DynamoDbBookRepository.updateBook preserves the item's
        // real creation timestamp itself; this value is never persisted.
        Book book = new Book(bookId, request.name(), request.priceCents(), request.count(),
                request.photoUrl(), request.visible(), Instant.now());
        bookRepository.updateBook(book);
        return BookResponse.from(book);
    }

    private void validate(String name, Long priceCents, Integer count) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (priceCents == null || priceCents <= 0) {
            throw new ValidationException("Price must be greater than 0");
        }
        if (count == null || count < 0) {
            throw new ValidationException("Count must be zero or greater");
        }
    }
}