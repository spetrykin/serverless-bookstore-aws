package com.serhii.bookstore.auth.exception;

import com.serhii.bookstore.common.error.ApiException;

public class UserBlockedException extends ApiException {

    public UserBlockedException() {
        super("User account is blocked");
    }

    @Override
    public int statusCode() {
        return 403;
    }

    @Override
    public String errorCode() {
        return "user_blocked";
    }
}
