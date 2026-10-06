package com.serhii.bookstore.recs.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.serhii.bookstore.common.error.ApiException;
import com.serhii.bookstore.common.security.AuthorizerContext;
import com.serhii.bookstore.common.web.ApiGatewayResponseFactory;
import com.serhii.bookstore.recs.dto.RecommendationResponse;
import com.serhii.bookstore.recs.service.RecommendationService;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code GET /recommendations} — any authenticated user, own recommendations only. */
@Component("recommendations")
public class RecommendationFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(RecommendationFunction.class);

    private final RecommendationService recommendationService;
    private final ApiGatewayResponseFactory responseFactory;

    public RecommendationFunction(RecommendationService recommendationService, ApiGatewayResponseFactory responseFactory) {
        this.recommendationService = recommendationService;
        this.responseFactory = responseFactory;
    }

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent event) {
        try {
            String userId = AuthorizerContext.from(event).userId();
            RecommendationResponse response = recommendationService.recommend(userId);
            log.info("recommendations succeeded, userId={}, source={}, count={}",
                    userId, response.source(), response.books().size());
            return responseFactory.success(200, response);
        } catch (ApiException e) {
            log.warn("recommendations rejected: errorCode={}, message={}", e.errorCode(), e.getMessage());
            return responseFactory.fromException(e);
        } catch (Exception e) {
            log.error("recommendations failed with unexpected exception", e);
            return responseFactory.internalError();
        }
    }
}
