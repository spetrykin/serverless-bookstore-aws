package com.serhii.bookstore.common.web;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import tools.jackson.databind.ObjectMapper;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.error.ErrorResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ApiGatewayResponseFactory {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayResponseFactory.class);
    private static final Map<String, String> JSON_HEADERS = Map.of("Content-Type", "application/json");

    private final ObjectMapper objectMapper;

    public ApiGatewayResponseFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public APIGatewayProxyResponseEvent success(int statusCode, Object body) {
        return respond(statusCode, body);
    }

    public APIGatewayProxyResponseEvent fromException(ApiException exception) {
        return respond(exception.statusCode(), new ErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    public APIGatewayProxyResponseEvent internalError() {
        return respond(500, new ErrorResponse("internal_error", "Unexpected server error"));
    }

    private APIGatewayProxyResponseEvent respond(int statusCode, Object body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(JSON_HEADERS)
                    .withBody(objectMapper.writeValueAsString(body))
                    // isBase64Encoded is a boxed Boolean (nullable) on this class; left
                    // unset it serializes as literal `null`, not `false`. HttpApi's v1.0
                    // response contract documents this field as strict true|false — a
                    // null value is a plausible reason for the generic 500 seen in
                    // production. See docs/incident-log.md for the investigation.
                    .withIsBase64Encoded(false);
        } catch (Exception serializationFailure) {
            log.error("failed to serialize response body for statusCode={}", statusCode, serializationFailure);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(JSON_HEADERS)
                    .withBody("{\"code\":\"internal_error\",\"message\":\"Unexpected server error\"}")
                    .withIsBase64Encoded(false);
        }
    }
}
