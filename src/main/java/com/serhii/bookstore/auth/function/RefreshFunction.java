package com.serhii.bookstore.auth.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import tools.jackson.databind.ObjectMapper;
import com.serhii.bookstore.auth.dto.RefreshRequest;
import com.serhii.bookstore.auth.dto.RefreshResponse;
import com.serhii.bookstore.auth.service.RefreshService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("refresh")
public class RefreshFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(RefreshFunction.class);

    private final RefreshService refreshService;
    private final ApiGatewayResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public RefreshFunction(RefreshService refreshService, ApiGatewayResponseFactory responseFactory, ObjectMapper objectMapper) {
        this.refreshService = refreshService;
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            RefreshRequest request = objectMapper.readValue(event.getBody(), RefreshRequest.class);
            RefreshResponse response = refreshService.refresh(request);
            log.info("refresh succeeded, returning statusCode=200");
            APIGatewayProxyResponseEvent result = responseFactory.success(200, response);
            log.info("refresh response before return: statusCode={}, bodyLength={}",
                    result.getStatusCode(), result.getBody() == null ? -1 : result.getBody().length());
            return result;
        } catch (ApiException e) {
            log.warn("refresh rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("refresh failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}
