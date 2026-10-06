package com.serhii.bookstore.catalog.service;

import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.repository.BookRepository;
import com.serhii.bookstore.common.web.Page;
import org.springframework.stereotype.Component;

/** User-facing storefront — visible books only, via {@code GSI1-visible-books}. */
@Component
public class CatalogService {

    private static final int PAGE_SIZE = 20;

    private final BookRepository bookRepository;

    public CatalogService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Page<BookResponse> listVisibleBooks(String cursor) {
        Page<Book> page;
        try {
            page = bookRepository.listVisible(cursor, PAGE_SIZE);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid pagination cursor");
        }
        return new Page<>(page.items().stream().map(BookResponse::from).toList(), page.nextCursor());
    }
}