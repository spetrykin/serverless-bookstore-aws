package com.serhii.bookstore.auth.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import tools.jackson.databind.ObjectMapper;
import com.serhii.bookstore.auth.dto.LoginRequest;
import com.serhii.bookstore.auth.dto.LoginResponse;
import com.serhii.bookstore.auth.service.LoginService;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("login")
public class LoginFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(LoginFunction.class);

    private final LoginService loginService;
    private final ApiGatewayResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public LoginFunction(LoginService loginService, ApiGatewayResponseFactory responseFactory, ObjectMapper objectMapper) {
        this.loginService = loginService;
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            LoginRequest request = objectMapper.readValue(event.getBody(), LoginRequest.class);
            LoginResponse response = loginService.login(request);
            log.info("login succeeded, userId={}, returning statusCode=200", response.userId());
            APIGatewayProxyResponseEvent result = responseFactory.success(200, response);
            log.info("login response before return: statusCode={}, bodyLength={}",
                    result.getStatusCode(), result.getBody() == null ? -1 : result.getBody().length());
            return result;
        } catch (ApiException e) {
            log.warn("login rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("login failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}
