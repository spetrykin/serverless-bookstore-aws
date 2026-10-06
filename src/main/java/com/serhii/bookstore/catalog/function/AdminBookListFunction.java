package com.serhii.bookstore.catalog.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.service.AdminBookService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.security.AuthorizerContext;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.common.web.RequestParams;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code GET /admin/books} — ADMIN only, full catalog (visible + hidden). */
@Component("adminBookList")
public class AdminBookListFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(AdminBookListFunction.class);

    private final AdminBookService adminBookService;
    private final ApiGatewayResponseFactory responseFactory;

    public AdminBookListFunction(AdminBookService adminBookService, ApiGatewayResponseFactory responseFactory) {
        this.adminBookService = adminBookService;
        this.responseFactory = responseFactory;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            AuthorizerContext.from(event).requireAdmin();
            String cursor = RequestParams.queryParam(event, "cursor");
            Page<BookResponse> page = adminBookService.listAllBooks(cursor);
            log.info("admin book list succeeded, itemCount={}, hasNextPage={}", page.items().size(), page.nextCursor() != null);
            return responseFactory.success(200, page);
        } catch (ApiException e) {
            log.warn("admin book list rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("admin book list failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}