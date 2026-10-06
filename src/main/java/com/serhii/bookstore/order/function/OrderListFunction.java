package com.serhii.bookstore.order.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.security.AuthorizerContext;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.common.web.RequestParams;
import com.serhii.bookstore.order.dto.OrderResponse;
import com.serhii.bookstore.order.service.OrderService;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code GET /orders} — any authenticated user, own orders only (userId from the authorizer context scopes the query's partition key). */
@Component("orderList")
public class OrderListFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderListFunction.class);

    private final OrderService orderService;
    private final ApiGatewayResponseFactory responseFactory;

    public OrderListFunction(OrderService orderService, ApiGatewayResponseFactory responseFactory) {
        this.orderService = orderService;
        this.responseFactory = responseFactory;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            String userId = AuthorizerContext.from(event).userId();
            String cursor = RequestParams.queryParam(event, "cursor");
            Page<OrderResponse> page = orderService.listOrders(userId, cursor);
            log.info("order list succeeded, userId={}, itemCount={}, hasNextPage={}", userId, page.items().size(), page.nextCursor() != null);
            return responseFactory.success(200, page);
        } catch (ApiException e) {
            log.warn("order list rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("order list failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}