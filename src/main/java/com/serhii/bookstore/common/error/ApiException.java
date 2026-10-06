package com.serhii.bookstore.common.error;

/**
 * Base for exceptions that map 1:1 to an HTTP status + error code via
 * {@link com.serhii.bookstore.common.web.ApiGatewayResponseFactory}, so
 * that class doesn't need to know about concrete exception types from
 * individual domains (e.g. {@code auth}).
 */
public abstract class ApiException extends RuntimeException {

    protected ApiException(String message) {
        super(message);
    }

    public abstract int statusCode();

    public abstract String errorCode();
}
