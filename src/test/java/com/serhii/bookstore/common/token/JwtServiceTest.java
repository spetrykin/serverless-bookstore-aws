package com.serhii.bookstore.common.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.serhii.bookstore.common.security.Role;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(Keys.hmacShaKeyFor(
            "0123456789abcdef0123456789abcdef".getBytes()));
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void issuesAndVerifiesAccessToken() {
        String token = jwtService.issueAccessToken("user-1", Role.ADMIN);

        DecodedToken decoded = jwtService.verify(token);

        assertThat(decoded.userId()).isEqualTo("user-1");
        assertThat(decoded.tokenUse()).isEqualTo(JwtService.TOKEN_USE_ACCESS);
        assertThat(decoded.role()).isEqualTo(Role.ADMIN);
        assertThat(decoded.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void issuesAndVerifiesRefreshToken() {
        JwtService.IssuedRefreshToken issued = jwtService.issueRefreshToken("user-1");

        DecodedToken decoded = jwtService.verify(issued.jwt());

        assertThat(decoded.userId()).isEqualTo("user-1");
        assertThat(decoded.jti()).isEqualTo(issued.jti());
        assertThat(decoded.tokenUse()).isEqualTo(JwtService.TOKEN_USE_REFRESH);
        assertThat(decoded.role()).isNull();
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        JwtProperties otherProperties = new JwtProperties(Keys.hmacShaKeyFor(
                "fedcba9876543210fedcba9876543210".getBytes()));
        JwtService otherJwtService = new JwtService(otherProperties);
        String token = otherJwtService.issueAccessToken("user-1", Role.USER);

        assertThatThrownBy(() -> jwtService.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.verify("not-a-jwt")).isInstanceOf(JwtException.class);
    }
}
