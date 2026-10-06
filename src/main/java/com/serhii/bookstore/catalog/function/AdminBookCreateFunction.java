package com.serhii.bookstore.catalog.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import tools.jackson.databind.ObjectMapper;
import com.serhii.bookstore.catalog.dto.BookResponse;
import com.serhii.bookstore.catalog.dto.CreateBookRequest;
import com.serhii.bookstore.catalog.service.AdminBookService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.security.AuthorizerContext;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code POST /admin/books} — ADMIN only. */
@Component("adminBookCreate")
public class AdminBookCreateFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(AdminBookCreateFunction.class);

    private final AdminBookService adminBookService;
    private final ApiGatewayResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public AdminBookCreateFunction(AdminBookService adminBookService, ApiGatewayResponseFactory responseFactory, ObjectMapper objectMapper) {
        this.adminBookService = adminBookService;
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            AuthorizerContext.from(event).requireAdmin();
            CreateBookRequest request = objectMapper.readValue(event.getBody(), CreateBookRequest.class);
            BookResponse response = adminBookService.createBook(request);
            log.info("admin book create succeeded, bookId={}, returning statusCode=201", response.bookId());
            return responseFactory.success(201, response);
        } catch (ApiException e) {
            log.warn("admin book create rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("admin book create failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}