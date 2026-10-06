package com.serhii.bookstore.catalog.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.service.CatalogService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.common.web.RequestParams;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code GET /books} — any authenticated user, visible books only. Auth is enforced entirely by BookstoreAuthorizer (DefaultAuthorizer); no role check here. */
@Component("catalogList")
public class CatalogListFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(CatalogListFunction.class);

    private final CatalogService catalogService;
    private final ApiGatewayResponseFactory responseFactory;

    public CatalogListFunction(CatalogService catalogService, ApiGatewayResponseFactory responseFactory) {
        this.catalogService = catalogService;
        this.responseFactory = responseFactory;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            String cursor = RequestParams.queryParam(event, "cursor");
            Page<BookResponse> page = catalogService.listVisibleBooks(cursor);
            log.info("catalog list succeeded, itemCount={}, hasNextPage={}", page.items().size(), page.nextCursor() != null);
            return responseFactory.success(200, page);
        } catch (ApiException e) {
            log.warn("catalog list rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("catalog list failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}