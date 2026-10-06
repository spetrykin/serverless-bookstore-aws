package com.serhii.bookstore.order.exception;

import com.serhii.bookstore.common.error.ApiException;

/** A book in the order's lines failed its conditional stock decrement — never thrown for a not-found/hidden book, only a real stock race (see catalog.exception.BookNotFoundException). */
public class InsufficientStockException extends ApiException {

    public InsufficientStockException() {
        super("One or more books in this order are out of stock");
    }

    @Override
    public int statusCode() {
        return 409;
    }

    @Override
    public String errorCode() {
        return "insufficient_stock";
    }
}