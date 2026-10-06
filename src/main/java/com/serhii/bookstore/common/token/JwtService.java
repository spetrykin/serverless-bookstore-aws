package com.serhii.bookstore.common.token;

import com.serhii.bookstore.common.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies access/refresh JWTs. Both token kinds are signed
 * (HS256) and self-contained; the refresh token's {@code jti} additionally
 * identifies a {@link RefreshTokenItem} row in DynamoDB, which is the actual
 * revocation control point (see {@link TokenService}).
 */
@Component
public class JwtService {

    public static final String CLAIM_TOKEN_USE = "token_use";
    public static final String CLAIM_ROLE = "role";
    public static final String TOKEN_USE_ACCESS = "access";
    public static final String TOKEN_USE_REFRESH = "refresh";

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String issueAccessToken(String userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .claim(CLAIM_TOKEN_USE, TOKEN_USE_ACCESS)
                .claim(CLAIM_ROLE, role.name())
                .signWith(properties.signingKey())
                .compact();
    }

    public IssuedRefreshToken issueRefreshToken(String userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.refreshTtl());
        String jti = UUID.randomUUID().toString();
        String jwt = Jwts.builder()
                .subject(userId)
                .id(jti)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_USE, TOKEN_USE_REFRESH)
                .signWith(properties.signingKey())
                .compact();
        return new IssuedRefreshToken(jwt, jti, expiresAt);
    }

    /**
     * Verifies signature and expiry only. Callers must additionally check
     * {@link DecodedToken#tokenUse()} matches what they expect, and (for
     * refresh tokens) that the corresponding {@link RefreshTokenItem} is
     * still {@code ACTIVE}.
     *
     * @throws JwtException if the token is malformed, expired, or has an
     *                       invalid signature.
     */
    public DecodedToken verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(properties.signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String roleClaim = claims.get(CLAIM_ROLE, String.class);
        return new DecodedToken(
                claims.getSubject(),
                claims.getId(),
                claims.get(CLAIM_TOKEN_USE, String.class),
                roleClaim == null ? null : Role.valueOf(roleClaim),
                claims.getExpiration().toInstant());
    }

    public record IssuedRefreshToken(String jwt, String jti, Instant expiresAt) {
    }
}
