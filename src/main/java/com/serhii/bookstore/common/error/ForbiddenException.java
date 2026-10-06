package com.serhii.bookstore.common.error;

public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        super("Admin role required");
    }

    @Override
    public int statusCode() {
        return 403;
    }

    @Override
    public String errorCode() {
        return "forbidden";
    }
}