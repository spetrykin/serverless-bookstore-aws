package com.serhii.bookstore.auth.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import tools.jackson.databind.ObjectMapper;
import com.serhii.bookstore.auth.dto.RegisterRequest;
import com.serhii.bookstore.auth.dto.RegisterResponse;
import com.serhii.bookstore.auth.service.RegistrationService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("register")
public class RegisterFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(RegisterFunction.class);

    private final RegistrationService registrationService;
    private final ApiGatewayResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public RegisterFunction(RegistrationService registrationService, ApiGatewayResponseFactory responseFactory, ObjectMapper objectMapper) {
        this.registrationService = registrationService;
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            RegisterRequest request = objectMapper.readValue(event.getBody(), RegisterRequest.class);
            RegisterResponse response = registrationService.register(request);
            log.info("register succeeded, userId={}, returning statusCode=201", response.userId());
            APIGatewayProxyResponseEvent result = responseFactory.success(201, response);
            log.info("register response before return: statusCode={}, bodyLength={}",
                    result.getStatusCode(), result.getBody() == null ? -1 : result.getBody().length());
            return result;
        } catch (ApiException e) {
            log.warn("register rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("register failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}
