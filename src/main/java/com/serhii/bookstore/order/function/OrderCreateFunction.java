package com.serhii.bookstore.order.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import tools.jackson.databind.ObjectMapper;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.security.AuthorizerContext;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import com.serhii.bookstore.order.dto.OrderResponse;
import com.serhii.bookstore.order.dto.PlaceOrderRequest;
import com.serhii.bookstore.order.service.OrderService;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code POST /orders} — any authenticated user, places an order for themselves (userId comes from the authorizer context, never the request body). */
@Component("orderCreate")
public class OrderCreateFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateFunction.class);

    private final OrderService orderService;
    private final ApiGatewayResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public OrderCreateFunction(OrderService orderService, ApiGatewayResponseFactory responseFactory, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            String userId = AuthorizerContext.from(event).userId();
            PlaceOrderRequest request = objectMapper.readValue(event.getBody(), PlaceOrderRequest.class);
            OrderResponse response = orderService.placeOrder(userId, request);
            log.info("order create succeeded, userId={}, orderId={}, returning statusCode=201", userId, response.orderId());
            return responseFactory.success(201, response);
        } catch (ApiException e) {
            log.warn("order create rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("order create failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}