package com.serhii.bookstore.catalog.repository;

import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.common.web.Page;
import java.util.Optional;

public interface BookRepository {

    /** Conditional create — fails if {@code book.bookId()} already exists. */
    void createBook(Book book);

    Optional<Book> findById(String bookId);

    /** Full replace of every non-key attribute; throws {@link com.serhii.bookstore.catalog.exception.BookNotFoundException} if the book doesn't exist. */
    void updateBook(Book book);

    /** Visible-only, via {@code GSI1-visible-books} — the user-facing catalog. */
    Page<Book> listVisible(String cursor, int limit);

    /** Every book, visible or hidden — the admin catalog (architecture-plan.md §6.2). */
    Page<Book> listAll(String cursor, int limit);
}