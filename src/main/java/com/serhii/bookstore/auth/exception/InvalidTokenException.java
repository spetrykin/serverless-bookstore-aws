package com.serhii.bookstore.auth.exception;

import com.serhii.bookstore.common.error.ApiException;

public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(message);
    }

    @Override
    public int statusCode() {
        return 401;
    }

    @Override
    public String errorCode() {
        return "invalid_token";
    }
}
