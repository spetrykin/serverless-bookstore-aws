package com.serhii.bookstore.auth.exception;

import com.serhii.bookstore.common.error.ApiException;

public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(message);
    }

    @Override
    public int statusCode() {
        return 400;
    }

    @Override
    public String errorCode() {
        return "validation_error";
    }
}
