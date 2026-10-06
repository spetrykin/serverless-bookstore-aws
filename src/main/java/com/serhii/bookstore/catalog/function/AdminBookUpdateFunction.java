package com.serhii.bookstore.catalog.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import tools.jackson.databind.ObjectMapper;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.dto.UpdateBookRequest;
import com.serhii.bookstore.catalog.exception.BookNotFoundException;
import com.serhii.bookstore.catalog.service.AdminBookService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.security.AuthorizerContext;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import com.serhii.bookstore.common.web.RequestParams;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code PUT /admin/books/{bookId}} — ADMIN only, full replace (name/price/count/photoUrl/visible). */
@Component("adminBookUpdate")
public class AdminBookUpdateFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(AdminBookUpdateFunction.class);

    private final AdminBookService adminBookService;
    private final ApiGatewayResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public AdminBookUpdateFunction(AdminBookService adminBookService, ApiGatewayResponseFactory responseFactory, ObjectMapper objectMapper) {
        this.adminBookService = adminBookService;
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            AuthorizerContext.from(event).requireAdmin();
            String bookId = RequestParams.pathParam(event, "bookId");
            if (bookId == null || bookId.isBlank()) {
                throw new BookNotFoundException("(missing bookId)");
            }
            UpdateBookRequest request = objectMapper.readValue(event.getBody(), UpdateBookRequest.class);
            BookResponse response = adminBookService.updateBook(bookId, request);
            log.info("admin book update succeeded, bookId={}", bookId);
            return responseFactory.success(200, response);
        } catch (ApiException e) {
            log.warn("admin book update rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("admin book update failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}