package com.serhii.bookstore.auth.exception;

import com.serhii.bookstore.common.error.ApiException;

/** Mirrors {@code catalog.exception.BookNotFoundException}'s style — admin user lookups (block/unblock/delete) that don't resolve to an existing {@code userId}. */
public class UserNotFoundException extends ApiException {

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
    }

    @Override
    public int statusCode() {
        return 404;
    }

    @Override
    public String errorCode() {
        return "user_not_found";
    }
}