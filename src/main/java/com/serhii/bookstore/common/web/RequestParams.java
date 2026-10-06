package com.serhii.bookstore.common.web;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import java.util.Map;

public final class RequestParams {

    private RequestParams() {
    }

    public static String queryParam(APIGatewayProxyRequestEvent event, String name) {
        Map<String, String> params = event.getQueryStringParameters();
        return params == null ? null : params.get(name);
    }

    public static String pathParam(APIGatewayProxyRequestEvent event, String name) {
        Map<String, String> params = event.getPathParameters();
        return params == null ? null : params.get(name);
    }
}