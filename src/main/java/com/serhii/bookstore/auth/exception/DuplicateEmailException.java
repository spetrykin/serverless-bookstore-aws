package com.serhii.bookstore.auth.exception;

import com.serhii.bookstore.common.error.ApiException;

public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException(String email) {
        super("Email already registered: " + email);
    }

    @Override
    public int statusCode() {
        return 409;
    }

    @Override
    public String errorCode() {
        return "duplicate_email";
    }
}
