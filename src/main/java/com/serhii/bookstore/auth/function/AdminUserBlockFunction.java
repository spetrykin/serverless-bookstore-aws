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

/** {@code POST /admin/users/{userId}/block} — ADMIN only; see {@code AdminUserUnblockFunction} for the reverse. */
@Component("adminUserBlock")
public class AdminUserBlockFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBlockFunction.class);

    private final AdminUserService adminUserService;
    private final ApiGatewayResponseFactory responseFactory;

    public AdminUserBlockFunction(AdminUserService adminUserService, ApiGatewayResponseFactory responseFactory) {
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
            adminUserService.blockUser(adminUserId, targetUserId);
            log.info("admin user block succeeded, targetUserId={}", targetUserId);
            return responseFactory.success(200, Map.of("userId", targetUserId, "status", "BLOCKED"));
        } catch (ApiException e) {
            log.warn("admin user block rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("admin user block failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}