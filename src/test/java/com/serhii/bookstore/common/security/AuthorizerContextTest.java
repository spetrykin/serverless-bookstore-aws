package com.serhii.bookstore.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.serhii.bookstore.common.error.ForbiddenException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorizerContextTest {

    private static APIGatewayProxyRequestEvent eventWithAuthorizerContext(Map<String, Object> authorizer) {
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        requestContext.setAuthorizer(authorizer);
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setRequestContext(requestContext);
        return event;
    }

    /** HttpApi's documented payload-1.0 shape: context nested under "lambda". */
    @Test
    void extractsFromNestedLambdaShape() {
        APIGatewayProxyRequestEvent event = eventWithAuthorizerContext(
                Map.of("lambda", Map.of("userId", "user-1", "role", "ADMIN")));

        AuthorizerContext context = AuthorizerContext.from(event);

        assertThat(context.userId()).isEqualTo("user-1");
        assertThat(context.role()).isEqualTo(Role.ADMIN);
    }

    /** Defensive fallback in case the real deployed shape turns out flat instead. */
    @Test
    void extractsFromFlatShapeAsFallback() {
        APIGatewayProxyRequestEvent event = eventWithAuthorizerContext(
                Map.of("userId", "user-1", "role", "USER"));

        AuthorizerContext context = AuthorizerContext.from(event);

        assertThat(context.userId()).isEqualTo("user-1");
        assertThat(context.role()).isEqualTo(Role.USER);
    }

    @Test
    void requireAdminPassesForAdmin() {
        AuthorizerContext context = new AuthorizerContext("user-1", Role.ADMIN);

        context.requireAdmin();
    }

    @Test
    void requireAdminRejectsRegularUser() {
        AuthorizerContext context = new AuthorizerContext("user-1", Role.USER);

        assertThatThrownBy(context::requireAdmin).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsMissingAuthorizerContext() {
        APIGatewayProxyRequestEvent event = eventWithAuthorizerContext(null);

        assertThatThrownBy(() -> AuthorizerContext.from(event)).isInstanceOf(ForbiddenException.class);
    }
}