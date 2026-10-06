package com.serhii.bookstore.auth.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.serhii.bookstore.auth.dto.AdminUserResponse;
import com.serhii.bookstore.auth.service.AdminUserService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.security.AuthorizerContext;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.common.web.RequestParams;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code GET /admin/users} — ADMIN only. */
@Component("adminUserList")
public class AdminUserListFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(AdminUserListFunction.class);

    private final AdminUserService adminUserService;
    private final ApiGatewayResponseFactory responseFactory;

    public AdminUserListFunction(AdminUserService adminUserService, ApiGatewayResponseFactory responseFactory) {
        this.adminUserService = adminUserService;
        this.responseFactory = responseFactory;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            AuthorizerContext.from(event).requireAdmin();
            String cursor = RequestParams.queryParam(event, "cursor");
            Page<AdminUserResponse> page = adminUserService.listUsers(cursor);
            log.info("admin user list succeeded, itemCount={}, hasNextPage={}", page.items().size(), page.nextCursor() != null);
            return responseFactory.success(200, page);
        } catch (ApiException e) {
            log.warn("admin user list rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("admin user list failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}