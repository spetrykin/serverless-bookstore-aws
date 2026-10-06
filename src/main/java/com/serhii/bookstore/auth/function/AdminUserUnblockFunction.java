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
 * {@code POST /admin/users/{userId}/unblock} — ADMIN only. Separate Lambda
 * from {@code AdminUserBlockFunction}, not a toggle endpoint — matches this
 * codebase's one-function-per-route granularity (every other admin action,
 * including book create vs. update, is its own function). Does not restore
 * refresh-token rows revoked by the block step; see
 * {@code AdminUserService.unblockUser}.
 */
@Component("adminUserUnblock")
public class AdminUserUnblockFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(AdminUserUnblockFunction.class);

    private final AdminUserService adminUserService;
    private final ApiGatewayResponseFactory responseFactory;

    public AdminUserUnblockFunction(AdminUserService adminUserService, ApiGatewayResponseFactory responseFactory) {
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
            adminUserService.unblockUser(adminUserId, targetUserId);
            log.info("admin user unblock succeeded, targetUserId={}", targetUserId);
            return responseFactory.success(200, Map.of("userId", targetUserId, "status", "ACTIVE"));
        } catch (ApiException e) {
            log.warn("admin user unblock rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("admin user unblock failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}