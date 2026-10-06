package com.serhii.bookstore.catalog.exception;

import com.serhii.bookstore.common.error.ApiException;

/**
 * Thrown both when a {@code bookId} doesn't exist at all and when it exists
 * but is hidden ({@code visible=false}) — deliberately not distinguished,
 * same no-enumeration reasoning as {@code LoginService}'s shared
 * {@code InvalidCredentialsException}.
 */
public class BookNotFoundException extends ApiException {

    public BookNotFoundException(String bookId) {
        super("Book not found: " + bookId);
    }

    @Override
    public int statusCode() {
        return 404;
    }

    @Override
    public String errorCode() {
        return "book_not_found";
    }
}