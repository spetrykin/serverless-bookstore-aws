package com.serhii.bookstore.catalog.dto;

import com.serhii.bookstore.catalog.domain.Book;

public record BookResponse(String bookId, String name, long priceCents, int count, String photoUrl, boolean visible) {

    public static BookResponse from(Book book) {
        return new BookResponse(book.bookId(), book.name(), book.priceCents(), book.count(), book.photoUrl(), book.visible());
    }
}