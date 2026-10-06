package com.serhii.bookstore.common.token;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.security.Keys;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

@Component
public class JwtProperties {

    private static final String ISSUER = "bookstore";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final SecretKey signingKey;

    /**
     * Fetches the key from SSM Parameter Store at cold start via the SDK,
     * not a CloudFormation {@code ssm-secure} dynamic reference — those
     * aren't supported inside Lambda {@code Environment.Variables} (see
     * architecture-plan.md §5.9). {@code JWT_SIGNING_KEY_PARAM} holds only
     * the parameter *name*, never the secret value itself.
     */
    public JwtProperties() {
        String parameterName = System.getenv("JWT_SIGNING_KEY_PARAM");
        if (parameterName == null || parameterName.isBlank()) {
            throw new IllegalStateException("JWT_SIGNING_KEY_PARAM environment variable is not set");
        }
        String encodedKey;
        try (SsmClient ssmClient = SsmClient.create()) {
            encodedKey = ssmClient.getParameter(GetParameterRequest.builder()
                            .name(parameterName)
                            .withDecryption(true)
                            .build())
                    .parameter()
                    .value();
        }
        this.signingKey = Keys.hmacShaKeyFor(Base64.getMimeDecoder().decode(encodedKey));
    }

    /** For tests — bypasses the env-var lookup above. */
    JwtProperties(SecretKey signingKey) {
        this.signingKey = signingKey;
    }

    public SecretKey signingKey() {
        return signingKey;
    }

    public String issuer() {
        return ISSUER;
    }

    public Duration accessTtl() {
        return ACCESS_TTL;
    }

    public Duration refreshTtl() {
        return REFRESH_TTL;
    }
}