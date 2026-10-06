package com.serhii.bookstore.auth.authorizer;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import com.serhii.bookstore.common.token.DecodedToken;
import com.serhii.bookstore.common.token.JwtService;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Stateless: this handler's own logic verifies the access JWT's
 * signature/expiry only, never reads item data from DynamoDB (see plan
 * §Context / architecture-plan.md §5.4). Its IAM role is NOT zero-DynamoDB
 * though — {@code dynamodb:DescribeTable} is required because
 * {@link com.serhii.bookstore.common.crac.DynamoDbCracResource} is an
 * unconditional bean shared across every function's Spring context and
 * fires its restore-time prime call here too (see architecture-plan.md
 * §5.11). Accepted trade-off: a blocked user's already-issued access token
 * still works until its own 15-minute TTL expires; only new refresh
 * requests are blocked immediately (RefreshService).
 *
 * <p>Returns {@link IamPolicyResponse} (the classic policy-document
 * authorizer contract), not HttpApi's newer "simple response" shape —
 * {@code EnableSimpleResponses} is deliberately off in template.yaml. This
 * reverses the original week-1 decision to use simple responses (see
 * architecture-plan.md §5.1 for the full reversal writeup): confirmed
 * against the actual bytecode of this project's exact
 * {@code spring-cloud-function-adapter-aws:5.0.3} dependency that its
 * {@code AWSLambdaUtils.isSupportedAWSType()} only skips wrapping a
 * function's return value in an {@code APIGatewayProxyResponseEvent}
 * envelope when the return type's package starts with
 * {@code com.amazonaws.services.lambda.runtime.events} — no custom type
 * (including this project's former {@code AuthorizerSimpleResponse}) can
 * ever satisfy that, so it was always going to get wrapped, which API
 * Gateway then rejects as a malformed authorizer response (500, before the
 * backend Lambda is ever invoked). {@code IamPolicyResponse} lives in that
 * exact package, so it round-trips unwrapped.
 */
@Component("authorize")
public class AuthorizerFunction implements Function<APIGatewayV2CustomAuthorizerEvent, IamPolicyResponse> {

    private final JwtService jwtService;

    public AuthorizerFunction(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public IamPolicyResponse apply(APIGatewayV2CustomAuthorizerEvent event) {
        String token = extractBearerToken(headerValue(event.getHeaders(), "authorization"));
        if (token == null) {
            return deny(event, "anonymous");
        }
        try {
            DecodedToken decoded = jwtService.verify(token);
            if (!JwtService.TOKEN_USE_ACCESS.equals(decoded.tokenUse()) || decoded.role() == null) {
                return deny(event, "anonymous");
            }
            return allow(event, decoded);
        } catch (JwtException e) {
            return deny(event, "anonymous");
        }
    }

    /** {@code role} in context is {@code role.name()} (a String), not the enum — see AuthorizerContext's javadoc on why. */
    private static IamPolicyResponse allow(APIGatewayV2CustomAuthorizerEvent event, DecodedToken decoded) {
        return IamPolicyResponse.builder()
                .withPrincipalId(decoded.userId())
                .withPolicyDocument(policyDocument(IamPolicyResponse.allowStatement(event.getRouteArn())))
                .withContext(Map.of("userId", decoded.userId(), "role", decoded.role().name()))
                .build();
    }

    private static IamPolicyResponse deny(APIGatewayV2CustomAuthorizerEvent event, String principalId) {
        return IamPolicyResponse.builder()
                .withPrincipalId(principalId)
                .withPolicyDocument(policyDocument(IamPolicyResponse.denyStatement(event.getRouteArn())))
                .build();
    }

    private static IamPolicyResponse.PolicyDocument policyDocument(IamPolicyResponse.Statement statement) {
        return IamPolicyResponse.PolicyDocument.builder()
                .withVersion(IamPolicyResponse.VERSION_2012_10_17)
                .withStatement(List.of(statement))
                .build();
    }

    private static String headerValue(Map<String, String> headers, String name) {
        return headers == null ? null : headers.get(name);
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return authorizationHeader.substring(7).trim();
    }
}