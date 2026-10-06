package com.serhii.bookstore.auth.exception;

import com.serhii.bookstore.common.error.ApiException;

/** Deliberately generic message — used for both "no such email" and "bad password" to avoid user enumeration. */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }

    @Override
    public int statusCode() {
        return 401;
    }

    @Override
    public String errorCode() {
        return "invalid_credentials";
    }
}
