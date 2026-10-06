package com.serhii.bookstore.auth.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.serhii.bookstore.auth.exception.UserNotFoundException;
import com.serhii.bookstore.auth.service.AdminUserService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.security.AuthorizerContext;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import com.serhii.bookstore.common.web.RequestParams;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code DELETE /admin/users/{userId}} — ADMIN only.
 *
 * <p><b>Access-token caveat, spelled out here specifically, not just inherited from §5.2's general TTL note:</b> deleting a
 * {@code UserItem} does not revoke an access token already in a client's
 * hands. Because the authorizer is stateless (§5.4 — no live DynamoDB read
 * per request), a deleted user's existing access token keeps authenticating
 * successfully against every protected route for up to its own 15-minute
 * TTL, even though the account no longer exists in DynamoDB. What becomes
 * impossible immediately is only *new* sessions — {@code /login} fails (no
 * {@code EmailAccountItem}), {@code /refresh} fails (no {@code UserItem} for
 * {@code userRepository.findById}) — not the token already issued.
 */
@Component("adminUserDelete")
public class AdminUserDeleteFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(AdminUserDeleteFunction.class);

    private final AdminUserService adminUserService;
    private final ApiGatewayResponseFactory responseFactory;

    public AdminUserDeleteFunction(AdminUserService adminUserService, ApiGatewayResponseFactory responseFactory) {
        this.adminUserService = adminUserService;
        this.responseFactory = responseFactory;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            AuthorizerContext authorizer = AuthorizerContext.from(event);
            authorizer.requireAdmin();
            String adminUserId = authorizer.userId();
            String targetUserId = RequestParams.pathParam(event, "userId");
            if (targetUserId == null || targetUserId.isBlank()) {
                throw new UserNotFoundException("(missing userId)");
            }
            adminUserService.deleteUser(adminUserId, targetUserId);
            log.info("admin user delete succeeded, targetUserId={}", targetUserId);
            return responseFactory.success(200, Map.of("userId", targetUserId, "deleted", true));
        } catch (ApiException e) {
            log.warn("admin user delete rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("admin user delete failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}