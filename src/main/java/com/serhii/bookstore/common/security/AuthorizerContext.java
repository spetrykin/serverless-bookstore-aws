package com.serhii.bookstore.common.security;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.serhii.bookstore.common.error.ForbiddenException;
import java.util.Map;

/**
 * Extracts {@code userId}/{@code role} that {@code BookstoreAuthorizer}
 * (see {@code auth.authorizer.AuthorizerFunction}) already put in the
 * request's authorizer context — Catalog/Order functions trust this and
 * never decode a JWT themselves.
 *
 * <p><b>Wire-shape caveat:</b> AWS documents HttpApi's payload-1.0 backend
 * integration as nesting the custom authorizer context under a
 * {@code "lambda"} key ({@code requestContext.authorizer.lambda.<key>}),
 * unlike REST API's flat {@code requestContext.authorizer.<key>}. This has
 * NOT been confirmed against this project's actual deployed wire format
 * (sam local start-api can't emulate a custom authorizer per
 * architecture-plan.md §5.8) — both shapes are handled defensively below
 * until a real deployed request settles it. Update this once verified.
 */
public record AuthorizerContext(String userId, Role role) {

    public static AuthorizerContext from(APIGatewayProxyRequestEvent event) {
        Map<String, Object> authorizerContext = extractContext(event);
        String userId = (String) authorizerContext.get("userId");
        String roleValue = (String) authorizerContext.get("role");
        return new AuthorizerContext(userId, Role.valueOf(roleValue));
    }

    public void requireAdmin() {
        if (role != Role.ADMIN) {
            throw new ForbiddenException();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractContext(APIGatewayProxyRequestEvent event) {
        Map<String, Object> authorizer = event.getRequestContext() == null
                ? null
                : event.getRequestContext().getAuthorizer();
        if (authorizer == null) {
            throw new ForbiddenException();
        }
        Object lambda = authorizer.get("lambda");
        return lambda instanceof Map ? (Map<String, Object>) lambda : authorizer;
    }
}